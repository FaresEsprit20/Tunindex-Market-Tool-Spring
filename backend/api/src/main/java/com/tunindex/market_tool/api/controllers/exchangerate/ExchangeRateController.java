package com.tunindex.market_tool.api.controllers.exchangerate;

import com.tunindex.market_tool.api.dto.exchangerate.ExchangeRateResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ExchangeRateController implements ExchangeRateApi {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/exchange-rates";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    public ExchangeRateResponseDto getRates() {
        log.info("API calling Collector for exchange rates");

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(ExchangeRateResponseDto.class)
                .block();
    }
}
