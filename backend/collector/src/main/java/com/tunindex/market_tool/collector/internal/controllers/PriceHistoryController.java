package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.history.PriceHistoryPointDto;
import com.tunindex.market_tool.collector.services.history.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/price-history")
@RequiredArgsConstructor
@Slf4j
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping("/{symbol}")
    public Mono<List<PriceHistoryPointDto>> get(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "180") int days,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        LocalDate from = LocalDate.now().minusDays(days);
        LocalDate to = LocalDate.now();

        if (refresh) {
            log.info("Collector: refreshing price history for {} ({} days)", symbol, days);
            return priceHistoryService.refreshAndGet(symbol, from, to);
        }

        List<PriceHistoryPointDto> stored = priceHistoryService.getStored(symbol, from);
        if (!stored.isEmpty()) {
            return Mono.just(stored);
        }
        // Nothing stored yet for this symbol — fetch once so the first
        // request isn't just an empty chart.
        return priceHistoryService.refreshAndGet(symbol, from, to);
    }

    /**
     * Closing prices for many symbols at once, keyed by symbol — what the
     * stock table's row sparklines are drawn from. Reads only what is
     * already stored: a table page must not trigger 20 scrapes.
     */
    @GetMapping("/sparklines")
    public Map<String, List<BigDecimal>> sparklines(
            @RequestParam String symbols,
            @RequestParam(defaultValue = "30") int days,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        List<String> requested = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .distinct()
                .limit(100)
                .toList();

        if (requested.isEmpty()) {
            return Map.of();
        }

        return priceHistoryService.getClosesForSymbols(requested, LocalDate.now().minusDays(days));
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("❌ Invalid or missing API key for internal price-history call");
            throw new SecurityException("Invalid or missing API key");
        }
    }
}
