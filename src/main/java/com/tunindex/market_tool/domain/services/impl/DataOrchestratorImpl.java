package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.config.properties.MarketToolProperties;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.providers.base.MarketDataProvider;
import com.tunindex.market_tool.domain.providers.investingcom.InvestingComProvider;
import com.tunindex.market_tool.domain.repository.jpa.StockRepository;
import com.tunindex.market_tool.domain.services.orchestrator.DataOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataOrchestratorImpl implements DataOrchestrator {

    private final MarketToolProperties properties;
    private final InvestingComProvider investingComProvider;
    private final StockRepository stockRepository;

    @Override
    public Mono<Void> runPipeline() {
        log.info("🚀 Running pipeline with provider: {}", getActiveProviderName());

        MarketDataProvider provider = getActiveProvider();

        return provider.fetchAllStocks()
                .collectList()
                .flatMap(enrichedStocks -> {
                    log.info("✅ Fetched {} stocks", enrichedStocks.size());
                    return saveAllToDatabase(enrichedStocks);
                })
                .doOnSuccess(v -> log.info("✅ Pipeline completed successfully"))
                .doOnError(e -> log.error("❌ Pipeline failed: {}", e.getMessage()))
                .then();
    }

    @Override
    public Mono<EnrichedStockData> runPipelineForStock(String symbol) {
        log.info("🚀 Running pipeline for stock: {}", symbol);

        MarketDataProvider provider = getActiveProvider();

        return provider.fetchStockData(symbol)
                .flatMap(enrichedData -> {
                    return Mono.fromCallable(() -> {
                                stockRepository.save(enrichedData.getStock());
                                log.info("Saved stock: {}", symbol);
                                return enrichedData;
                            })
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .doOnSuccess(stock -> log.info("✅ Successfully processed stock: {}", symbol))
                .doOnError(e -> log.error("❌ Failed to process stock {}: {}", symbol, e.getMessage()));
    }

    @Override
    public String getActiveProviderName() {
        String activeProvider = properties.getProvider().getActive();
        if (activeProvider == null || activeProvider.isEmpty()) {
            return Constants.PROVIDER_INVESTINGCOM;
        }
        return activeProvider;
    }

    private MarketDataProvider getActiveProvider() {
        String activeProvider = getActiveProviderName();

        if (Constants.PROVIDER_INVESTINGCOM.equalsIgnoreCase(activeProvider)) {
            return investingComProvider;
        }

        log.warn("Provider {} not found, falling back to InvestingCom", activeProvider);
        return investingComProvider;
    }

    private Mono<Void> saveAllToDatabase(List<EnrichedStockData> stocks) {
        log.info("💾 Saving {} stocks to database", stocks.size());

        return Flux.fromIterable(stocks)
                .parallel(properties.getParallelism().getMaxWorkers())
                .runOn(Schedulers.boundedElastic())
                .flatMap(enrichedData -> {
                    if (enrichedData.getStock() != null) {
                        return Mono.fromCallable(() -> {
                                    stockRepository.save(enrichedData.getStock());
                                    log.debug("Saved stock: {}", enrichedData.getStock().getSymbol());
                                    return enrichedData;
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .onErrorResume(e -> {
                                    log.error("Failed to save stock {}: {}",
                                            enrichedData.getStock().getSymbol(), e.getMessage());
                                    return Mono.empty();
                                });
                    }
                    return Mono.empty();
                })
                .sequential()
                .then()
                .doOnSuccess(v -> log.info("✅ All stocks saved successfully"));
    }
}