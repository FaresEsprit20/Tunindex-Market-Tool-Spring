package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.market.MarketBreadthDto;
import com.tunindex.market_tool.collector.dto.market.MarketSessionDto;
import com.tunindex.market_tool.collector.dto.market.UnusualActivityDto;
import com.tunindex.market_tool.collector.dto.news.MarketNewsDto;
import com.tunindex.market_tool.collector.services.market.MarketBreadthService;
import com.tunindex.market_tool.collector.services.market.MarketSessionService;
import com.tunindex.market_tool.collector.services.market.QuoteRefreshService;
import com.tunindex.market_tool.collector.services.market.UnusualActivityService;
import com.tunindex.market_tool.collector.services.news.MarketNewsService;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/internal/market")
@RequiredArgsConstructor
@Slf4j
public class MarketController {

    private final MarketSessionService marketSessionService;
    private final MarketNewsService marketNewsService;
    private final MarketBreadthService marketBreadthService;
    private final UnusualActivityService unusualActivityService;
    private final QuoteRefreshService quoteRefreshService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping("/session")
    public MarketSessionDto session(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return marketSessionService.currentSession();
    }

    @GetMapping("/news")
    public Mono<List<MarketNewsDto>> news(
            @RequestParam(defaultValue = "15") int limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return marketNewsService.getMarketNews(Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/breadth")
    public MarketBreadthDto breadth(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return marketBreadthService.breadth();
    }

    @GetMapping("/unusual")
    public List<UnusualActivityDto> unusual(
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return unusualActivityService.scan(limit);
    }

    /**
     * Symbols whose exchange page we have not read successfully within the
     * window. These are excluded from breadth and movers, so this endpoint is
     * how an operator finds out a name has gone dark — rather than noticing a
     * wrong figure in the UI.
     */
    @GetMapping("/stale-quotes")
    public List<String> staleQuotes(
            @RequestParam(defaultValue = "30") int olderThanHours,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        return quoteRefreshService.staleSymbols(java.time.Duration.ofHours(olderThanHours));
    }

    /**
     * Forces a quote pass now instead of waiting for the schedule. Blocking
     * and slow by design — it walks every symbol with a politeness delay.
     */
    @PostMapping("/refresh-quotes")
    public List<String> refreshQuotes(@RequestHeader(value = "X-API-Key", required = false) String apiKey) {
        validateApiKey(apiKey);
        quoteRefreshService.refreshNow();
        return quoteRefreshService.staleSymbols(java.time.Duration.ofHours(30));
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
