package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.services.backfill.BackfillService;
import com.tunindex.market_tool.collector.services.fundamentals.FundamentalsFallbackService;
import com.tunindex.market_tool.collector.services.fundamentals.PriceDerivedMetricsService;
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
    private final FundamentalsFallbackService fundamentalsFallbackService;
    private final PriceDerivedMetricsService priceDerivedMetricsService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    /**
     * Re-runs only the gap-filling passes, without re-scraping price history.
     *
     * <p>Useful after a parser change: the expensive part is the history
     * download, and this reconsiders every blank field against the second
     * source and the arithmetic derivations using the data already stored.
     */
    @PostMapping("/fill-gaps")
    public Map<String, Object> fillGaps(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        int metricsUpdated = priceDerivedMetricsService.refreshAll();
        Map<String, Integer> filled = fundamentalsFallbackService.fillGaps();

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("priceDerivedMetricsUpdated", metricsUpdated);
        response.put("fallbackFilled", filled);
        return response;
    }

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
