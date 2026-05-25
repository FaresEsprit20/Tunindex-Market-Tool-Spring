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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final OAuth2AuthenticationFilter oauth2AuthFilter;  // Changed from JwtAuthenticationFilter
    private final AuthenticationProvider authProvider;
    private final RateLimitingFilter rateLimitingFilter;
    private final InputSanitizerFilter inputSanitizerFilter;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
//    private final RecaptchaFilter recaptchaFilter;

    private static final String[] WHITE_LIST = {
            "/tunindex/market/tool/v1/auth/**",
            "/tunindex/market/tool/v1/stocks/auth/**",
            "/tunindex/market/tool/v1/stocks/accounts/management/**",
            "/tunindex/market/tool/v1/accounts/management/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/oauth2/**",           // OAuth2 endpoints
            "/login/oauth2/**",     // OAuth2 login endpoints
            "/oauth2/success",      // OAuth2 success redirect
            "/oauth2/error"         // OAuth2 error redirect
    };

    // Add internal endpoints that don't need authentication
    private static final String[] INTERNAL_WHITE_LIST = {
            "/internal/**"  // Allow all internal calls without authentication
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .authorizeHttpRequests(auth -> auth
                        // Allow internal endpoints without authentication
                        .requestMatchers(INTERNAL_WHITE_LIST).permitAll()
                        // Allow public endpoints
                        .requestMatchers(WHITE_LIST).permitAll()
                        // Everything else needs authentication
                        .anyRequest().authenticated()
                )
                // OAuth2 Login Configuration
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2LoginSuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            response.sendRedirect("/oauth2/error?message=" + exception.getMessage());
                        })
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
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authenticationProvider(authProvider)
                // Add OAuth2 filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(oauth2AuthFilter, UsernamePasswordAuthenticationFilter.class)
                // Optional: Add rate limiting and input sanitization filters
                .addFilterBefore(rateLimitingFilter, OAuth2AuthenticationFilter.class)
                .addFilterBefore(inputSanitizerFilter, rateLimitingFilter.getClass());

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