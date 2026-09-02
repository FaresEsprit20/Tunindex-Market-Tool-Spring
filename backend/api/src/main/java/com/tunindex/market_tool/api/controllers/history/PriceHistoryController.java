package com.tunindex.market_tool.api.controllers.history;

import com.tunindex.market_tool.api.dto.history.PriceHistoryPointResponseDto;
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
public class PriceHistoryController implements PriceHistoryApi {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/price-history";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    public List<PriceHistoryPointResponseDto> get(String symbol, int days, boolean refresh) {
        log.info("API calling Collector for price history: {} (days={}, refresh={})", symbol, days, refresh);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/{symbol}?days={days}&refresh={refresh}", symbol, days, refresh)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<PriceHistoryPointResponseDto>>() {})
                // ilboursa's own two-step scrape can be slow the first time
                // for a symbol with nothing cached yet.
                .timeout(Duration.ofSeconds(35))
                .block();
    }
}
