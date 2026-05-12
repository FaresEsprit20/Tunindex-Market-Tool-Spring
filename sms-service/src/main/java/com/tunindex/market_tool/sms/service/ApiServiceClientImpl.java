package com.tunindex.market_tool.sms.service;

import com.tunindex.market_tool.sms.dto.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiServiceClientImpl implements ApiServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${api.service.url:http://api-service}")
    private String apiServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Override
    public List<UserEmailDto> getAllUserEmails() {
        log.info("Calling API service to fetch all user emails");

        try {
            return webClientBuilder.build()
                    .get()
                    .uri(apiServiceUrl + "/internal/users/mailing/emails")
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<UserEmailDto>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch user emails from API service: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<UserEmailDto> getUserEmailsByRole(UserRole role) {
        log.info("Calling API service to fetch user emails by role: {}", role);

        try {
            return webClientBuilder.build()
                    .get()
                    .uri(apiServiceUrl + "/internal/users/mailing/emails/by-role?role=" + role.name())
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<UserEmailDto>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch user emails by role from API service: {}", e.getMessage());
            return List.of();
        }
    }

}