package com.tunindex.market_tool.api.controllers.scoring;

import com.tunindex.market_tool.api.dto.scoring.OpportunityScoreResponseDto;
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
public class ScoringController implements ScoringApi {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/scoring";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    public List<OpportunityScoreResponseDto> opportunities(int limit, int minScore, boolean includeNews) {
        log.info("API calling Collector for opportunities (limit={}, minScore={})", limit, minScore);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/opportunities?limit={limit}&minScore={minScore}&includeNews={includeNews}",
                        limit, minScore, includeNews)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<OpportunityScoreResponseDto>>() {})
                // Scores every tracked stock and recomputes technicals from
                // stored history, so this is heavier than a plain lookup.
                .timeout(Duration.ofSeconds(45))
                .block();
    }

    @Override
    public OpportunityScoreResponseDto score(String symbol) {
        log.info("API calling Collector for score: {}", symbol);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/score/{symbol}", symbol)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(OpportunityScoreResponseDto.class)
                .timeout(Duration.ofSeconds(25))
                .block();
    }
}
