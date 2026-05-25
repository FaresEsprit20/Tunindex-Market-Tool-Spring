package com.tunindex.market_tool.api.config.security.config;

import com.tunindex.market_tool.api.config.security.filters.InputSanitizerFilter;
import com.tunindex.market_tool.api.config.security.filters.OAuth2AuthenticationFilter;
import com.tunindex.market_tool.api.config.security.filters.RateLimitingFilter;
import com.tunindex.market_tool.api.config.security.handler.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final OAuth2AuthenticationFilter oauth2AuthFilter;
    private final AuthenticationProvider authProvider;
    private final RateLimitingFilter rateLimitingFilter;
    private final InputSanitizerFilter inputSanitizerFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2ResourceServer oAuth2ResourceServer;  // Add Resource Server

    private static final String[] WHITE_LIST = {
            "/tunindex/market/tool/v1/auth/**",
            "/tunindex/market/tool/v1/stocks/auth/**",
            "/tunindex/market/tool/v1/stocks/accounts/management/**",
            "/tunindex/market/tool/v1/accounts/management/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/oauth2/**",
            "/login/oauth2/**",
            "/oauth2/success",
            "/oauth2/error"
    };

    private static final String[] INTERNAL_WHITE_LIST = {
            "/internal/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // OAuth2 Resource Server (validates opaque tokens and loads roles)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaqueToken -> opaqueToken
                                .introspector(oAuth2ResourceServer)
                        )
                )

                // OAuth2 Login Configuration
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            response.sendRedirect("/oauth2/error?message=" + exception.getMessage());
                        })
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(INTERNAL_WHITE_LIST).permitAll()
                        .requestMatchers(WHITE_LIST).permitAll()
                        .anyRequest().authenticated()
                )

                // Logout Configuration
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(200);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"success\": true, \"message\": \"Logged out successfully\"}");
                            response.getWriter().flush();
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("accessToken", "refreshToken", "JSESSIONID")
                )

                .authenticationProvider(authProvider)
                .addFilterBefore(oauth2AuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitingFilter, OAuth2AuthenticationFilter.class)
                .addFilterBefore(inputSanitizerFilter, RateLimitingFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://localhost:3000",
                "https://app.myapp.com",
                "https://admin.myapp.com"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList(
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With",
                "Authorization",
                "X-API-Key"
        ));
        config.setExposedHeaders(Arrays.asList("Content-Disposition", "Content-Type", "Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}