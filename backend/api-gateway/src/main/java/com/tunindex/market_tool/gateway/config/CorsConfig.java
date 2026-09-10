package com.tunindex.market_tool.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS, defined once for the whole platform.
 *
 * <p>Previously each service carried its own copy, which is how they drift:
 * one allows an origin another does not, and the symptom is a request that
 * works against one endpoint and fails against the next for no reason the
 * caller can see. With everything entering through the gateway there is one
 * definition to keep right.
 */
@Configuration
public class CorsConfig {

    /**
     * Origins allowed to call the API, as an explicit list.
     *
     * <p>Never a wildcard here. The browser refuses to send cookies to a
     * wildcard origin, and this platform authenticates with HttpOnly cookies -
     * so "*" would not loosen security, it would simply break every
     * authenticated request while appearing more permissive.
     */
    @Value("${gateway.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Required for the cookie-based session this platform uses.
        config.setAllowCredentials(true);
        // So the browser can read the trace id off a failed call.
        config.setExposedHeaders(List.of("X-Correlation-Id", "X-RateLimit-Remaining", "Retry-After"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
