package com.tunindex.market_tool.api.controllers.news;

import com.tunindex.market_tool.api.dto.news.NewsImpactResponseDto;
import com.tunindex.market_tool.api.dto.news.StockNewsResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class StockNewsController implements StockNewsApi {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/news";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    public List<StockNewsResponseDto> get(String symbol, int limit) {
        log.info("API calling Collector for news: {} (limit={})", symbol, limit);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/{symbol}?limit={limit}", symbol, limit)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<StockNewsResponseDto>>() {})
                .timeout(Duration.ofSeconds(25))
                .block();
    }

    @Override
    public List<NewsImpactResponseDto> getImpact(String symbol, int limit, int tradingDaysAfter) {
        log.info("API calling Collector for news impact: {} (limit={}, tradingDaysAfter={})", symbol, limit, tradingDaysAfter);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/{symbol}/impact?limit={limit}&tradingDaysAfter={tradingDaysAfter}",
                        symbol, limit, tradingDaysAfter)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<NewsImpactResponseDto>>() {})
                .timeout(Duration.ofSeconds(25))
                .block();
    }
}
