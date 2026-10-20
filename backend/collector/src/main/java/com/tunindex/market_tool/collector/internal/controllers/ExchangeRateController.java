package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.exchangerate.ExchangeRateResponseDto;
import com.tunindex.market_tool.collector.services.fx.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/internal/exchange-rates")
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping
    public Mono<ExchangeRateResponseDto> getRates(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return exchangeRateService.getRates();
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("❌ Invalid or missing API key for internal exchange-rate call");
            throw new SecurityException("Invalid or missing API key");
        }
    }
}
