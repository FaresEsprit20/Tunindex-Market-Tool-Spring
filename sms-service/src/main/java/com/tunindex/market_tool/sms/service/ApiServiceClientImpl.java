package com.tunindex.market_tool.sms.service;

import com.tunindex.market_tool.sms.dto.UserPhoneDto;
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
    public List<UserPhoneDto> getAllPhoneNumbers() {
        log.info("Calling API service to fetch all user phone numbers");

        try {
            return webClientBuilder.build()
                    .get()
                    .uri(apiServiceUrl + "/internal/users/sms/phone-numbers")
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<UserPhoneDto>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch user phone numbers from API service: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<UserPhoneDto> getPhoneNumbersByRole(UserRole role) {
        log.info("Calling API service to fetch user phone numbers by role: {}", role);

        try {
            return webClientBuilder.build()
                    .get()
                    .uri(apiServiceUrl + "/internal/users/sms/phone-numbers/by-role?role=" + role.name())
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<UserPhoneDto>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch user phone numbers by role from API service: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public UserPhoneDto getPhoneNumberByEmail(String email) {
        log.info("Calling API service to fetch phone number for email: {}", email);

        try {
            return webClientBuilder.build()
                    .get()
                    .uri(apiServiceUrl + "/internal/users/sms/phone-number?email=" + email)
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(UserPhoneDto.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch phone number by email from API service: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public UserPhoneDto getPhoneNumberByUserId(Long userId) {
        log.info("Calling API service to fetch phone number for user ID: {}", userId);

        try {
            return webClientBuilder.build()
                    .get()
                    .uri(apiServiceUrl + "/internal/users/sms/phone-number/" + userId)
                    .header("X-API-Key", internalApiKey)
                    .retrieve()
                    .bodyToMono(UserPhoneDto.class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch phone number by user ID from API service: {}", e.getMessage());
            return null;
        }
    }


}