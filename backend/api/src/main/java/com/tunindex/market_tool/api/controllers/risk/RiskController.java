package com.tunindex.market_tool.api.controllers.risk;

import com.tunindex.market_tool.api.dto.risk.CorrelationMatrixResponseDto;
import com.tunindex.market_tool.api.dto.risk.RiskMetricsResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

/**
 * Risk statistics for a single name, and correlation across several.
 *
 * <p>Longer timeouts than the quote endpoints on purpose: correlation over a
 * large symbol set walks several years of stored closes, and a 15-second cap
 * would turn a slow-but-correct answer into an error.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Risk", description = "Volatility, drawdown, beta and correlation from stored price history")
public class RiskController {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/risk";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @GetMapping(value = APP_ROOT + "/risk/metrics/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Volatility, drawdown, beta, Sharpe and VaR for one symbol")
    public RiskMetricsResponseDto metrics(
            @PathVariable String symbol,
            @RequestParam(value = "windowDays", defaultValue = "365") int windowDays) {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/metrics/{symbol}?windowDays={windowDays}", symbol, windowDays)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(RiskMetricsResponseDto.class)
                .timeout(Duration.ofSeconds(45))
                .block();
    }

    @GetMapping(value = APP_ROOT + "/risk/correlation", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Pairwise return correlation across a set of symbols")
    public CorrelationMatrixResponseDto correlation(
            @RequestParam("symbols") String symbols,
            @RequestParam(value = "windowDays", defaultValue = "365") int windowDays) {
        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/correlation?symbols={symbols}&windowDays={windowDays}", symbols, windowDays)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(CorrelationMatrixResponseDto.class)
                .timeout(Duration.ofSeconds(60))
                .block();
    }
}
