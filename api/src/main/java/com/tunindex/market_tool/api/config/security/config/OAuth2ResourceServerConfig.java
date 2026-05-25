package com.tunindex.market_tool.api.config.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionAuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class OAuth2ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.introspection-uri}")
    private String introspectionUri;

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.resourceserver.opaquetoken.client-secret}")
    private String clientSecret;

    private final WebClient.Builder webClientBuilder;

    /**
     * Modern OpaqueTokenIntrospector using WebClient (not deprecated)
     */
    @Bean
    public OpaqueTokenIntrospector opaqueTokenIntrospector() {
        return token -> {
            try {
                // Call introspection endpoint
                Map<String, Object> response = callIntrospectionEndpoint(token);

                // Check if token is active
                boolean active = (boolean) response.getOrDefault("active", false);
                if (!active) {
                    throw new RuntimeException("Token is not active");
                }

                // Extract user info
                String username = (String) response.get("username");
                if (username == null) {
                    username = (String) response.get("sub");
                }

                // Extract scopes/authorities
                Collection<GrantedAuthority> authorities = extractAuthorities(response);

                // Build attributes
                Map<String, Object> attributes = new HashMap<>(response);
                attributes.put("active", active);

                // Create authenticated principal
                return new OAuth2IntrospectionAuthenticatedPrincipal(
                        username,
                        attributes,
                        authorities
                );

            } catch (Exception e) {
                log.error("Failed to introspect token: {}", e.getMessage());
                throw new RuntimeException("Failed to introspect token", e);
            }
        };
    }

    private Map<String, Object> callIntrospectionEndpoint(String token) {
        // Use WebClient to call introspection endpoint
        return webClientBuilder.build()
                .post()
                .uri(introspectionUri)
                .header("Authorization", "Basic " + encodeClientCredentials())
                .bodyValue(Map.of("token", token))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
    }

    private String encodeClientCredentials() {
        String credentials = clientId + ":" + clientSecret;
        return java.util.Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private Collection<GrantedAuthority> extractAuthorities(Map<String, Object> response) {
        // Extract from "authorities" claim
        Object authoritiesObj = response.get("authorities");
        if (authoritiesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> authorities = (List<String>) authoritiesObj;
            return authorities.stream()
                    .map(auth -> new SimpleGrantedAuthority("ROLE_" + auth))
                    .collect(Collectors.toList());
        }

        // Extract from "scope" claim
        Object scopeObj = response.get("scope");
        if (scopeObj instanceof String) {
            String scope = (String) scopeObj;
            return List.of(scope.split(" "))
                    .stream()
                    .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}