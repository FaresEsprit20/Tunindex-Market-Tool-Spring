package com.tunindex.market_tool.api.controllers.analysis;

import com.tunindex.market_tool.api.dto.analysis.FundamentalAnalysisResponseDto;
import com.tunindex.market_tool.api.dto.analysis.TechnicalAnalysisResponseDto;
import com.tunindex.market_tool.api.dto.analyst.TradeSetupResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AnalysisController implements AnalysisApi {

    private final WebClient.Builder webClientBuilder;
    private static final String COLLECTOR_URL = "http://collector-service/internal/analysis";
    private static final String ANALYST_URL = "http://collector-service/internal/analyst";

    @Value("${internal.api.key:market-tool-internal-secret-key-2024}")
    private String internalApiKey;

    @Override
    public TechnicalAnalysisResponseDto technical(String symbol, int bars) {
        log.info("API calling Collector for technical analysis: {} (bars={})", symbol, bars);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/{symbol}/technical?bars={bars}", symbol, bars)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(TechnicalAnalysisResponseDto.class)
                .timeout(Duration.ofSeconds(15))
                .block();
    }

    /**
     * The Tradify Analyst's plan for a stock.
     *
     * <p>Lives on the analysis path rather than under scoring because it is
     * about one symbol the reader is already looking at, not a ranking.
     */
    @Override
    public TradeSetupResponseDto setup(String symbol) {
        log.info("API calling Collector for the analyst setup: {}", symbol);

        return webClientBuilder.build()
                .get()
                .uri(ANALYST_URL + "/{symbol}/setup", symbol)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(TradeSetupResponseDto.class)
                .timeout(Duration.ofSeconds(15))
                .block();
    }

    @Override
    public FundamentalAnalysisResponseDto fundamental(String symbol) {
        log.info("API calling Collector for fundamental analysis: {}", symbol);

        return webClientBuilder.build()
                .get()
                .uri(COLLECTOR_URL + "/{symbol}/fundamental", symbol)
                .header("X-API-Key", internalApiKey)
                .retrieve()
                .bodyToMono(FundamentalAnalysisResponseDto.class)
                .timeout(Duration.ofSeconds(15))
                .block();
    }
}
