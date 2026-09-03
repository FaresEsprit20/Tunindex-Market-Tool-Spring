package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.market.MarketSessionDto;
import com.tunindex.market_tool.collector.dto.news.MarketNewsDto;
import com.tunindex.market_tool.collector.services.market.MarketSessionService;
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

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            throw new InvalidEntityException(
                    "Invalid internal API key",
                    ErrorCodes.INVALID_PARAMETER,
                    Collections.singletonList("A valid X-API-Key header is required"));
        }
    }
}
