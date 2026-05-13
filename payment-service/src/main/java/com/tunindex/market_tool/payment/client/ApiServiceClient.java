package com.tunindex.market_tool.payment.client;

import com.tunindex.market_tool.payment.dto.UserPaymentInfoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${api.service.url:http://api-service}")
    private String apiServiceUrl;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public UserPaymentInfoDto getUserPaymentInfo(Long userId) {
        log.info("Fetching user payment info for userId: {}", userId);

        return webClientBuilder.build()
                .get()
                .uri(apiServiceUrl + "/internal/users/payment/" + userId)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(UserPaymentInfoDto.class)
                .block();
    }
    
}