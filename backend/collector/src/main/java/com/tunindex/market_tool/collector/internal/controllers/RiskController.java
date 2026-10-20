package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.risk.CorrelationMatrixDto;
import com.tunindex.market_tool.collector.dto.risk.RiskMetricsDto;
import com.tunindex.market_tool.collector.services.risk.RiskAnalyticsService;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Risk statistics over stored price history. Internal-only, like every other
 * collector endpoint — the public API service proxies these.
 */
@RestController
@RequestMapping("/internal/risk")
@RequiredArgsConstructor
@Slf4j
public class RiskController {

    private final RiskAnalyticsService riskAnalyticsService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping("/metrics/{symbol}")
    public RiskMetricsDto metrics(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "365") int windowDays,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return riskAnalyticsService.riskMetrics(symbol, windowDays);
    }

    @GetMapping("/correlation")
    public CorrelationMatrixDto correlation(
            @RequestParam String symbols,
            @RequestParam(defaultValue = "365") int windowDays,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return riskAnalyticsService.correlationMatrix(
                Arrays.asList(symbols.split(",")), windowDays);
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            throw new InvalidEntityException(
                    "Invalid internal API key",
                    ErrorCodes.INVALID_PARAMETER,
                    Collections.singletonList("A valid X-API-Key header is required"));
        }
    }
}
