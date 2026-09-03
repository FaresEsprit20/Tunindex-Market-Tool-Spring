package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.services.backfill.BackfillService;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/internal/backfill")
@RequiredArgsConstructor
@Slf4j
public class BackfillController {

    private final BackfillService backfillService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    /** Kicks off a background backfill of price history (and news) for every symbol. */
    @PostMapping("/start")
    public Map<String, Object> start(
            @RequestParam(defaultValue = "365") int historyDays,
            @RequestParam(defaultValue = "true") boolean includeNews,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        boolean started = backfillService.start(historyDays, includeNews);
        Map<String, Object> response = new java.util.LinkedHashMap<>(backfillService.status());
        response.put("started", started);
        return response;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return backfillService.status();
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
