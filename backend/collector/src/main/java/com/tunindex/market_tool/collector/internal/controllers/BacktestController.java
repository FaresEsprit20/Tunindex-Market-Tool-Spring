package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.backtest.BacktestResultDto;
import com.tunindex.market_tool.collector.services.backtest.BacktestService;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@RestController
@RequestMapping("/internal/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestService backtestService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping
    public BacktestResultDto run(
            @RequestParam(defaultValue = "30") int horizonDays,
            @RequestParam(defaultValue = "10") int stepDays,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        return backtestService.run(
                Math.min(Math.max(horizonDays, 5), 180),
                Math.min(Math.max(stepDays, 1), 60));
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
