package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.news.NewsImpactDto;
import com.tunindex.market_tool.collector.dto.news.StockNewsDto;
import com.tunindex.market_tool.collector.services.news.NewsImpactService;
import com.tunindex.market_tool.collector.services.news.StockNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/internal/news")
@RequiredArgsConstructor
@Slf4j
public class StockNewsController {

    private final StockNewsService stockNewsService;
    private final NewsImpactService newsImpactService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @GetMapping("/{symbol}")
    public Mono<List<StockNewsDto>> get(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        return stockNewsService.getNews(symbol, limit);
    }

    @GetMapping("/{symbol}/impact")
    public Mono<List<NewsImpactDto>> getImpact(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "3") int tradingDaysAfter,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        return newsImpactService.getImpact(symbol, limit, tradingDaysAfter);
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            log.warn("❌ Invalid or missing API key for internal news call");
            throw new SecurityException("Invalid or missing API key");
        }
    }
}
