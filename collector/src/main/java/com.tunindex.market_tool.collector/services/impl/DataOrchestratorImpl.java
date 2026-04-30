package com.tunindex.market_tool.collector.services.impl;

import com.tunindex.market_tool.collector.providers.stockanalysis.StockAnalysisProvider;
import com.tunindex.market_tool.common.utils.constants.Constants;
import com.tunindex.market_tool.common.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.common.entities.Stock;
import com.tunindex.market_tool.common.repository.jpa.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.tunindex.market_tool.collector.services.orchestrator.DataOrchestrator;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataOrchestratorImpl implements DataOrchestrator {

    private StockRepository stockRepository;
    private final StockAnalysisProvider stockAnalysisProvider;

    @Override
    public Mono<Void> runPipeline() {
        log.info("🚀 Running pipeline with provider: {}", getActiveProviderName());

        return stockAnalysisProvider.fetchAllStocks()
                .collectList()
                .flatMap(this::saveAllToDatabase)
                .doOnSuccess(v -> log.info("✅ Pipeline completed successfully"))
                .doOnError(e -> log.error("❌ Pipeline failed: {}", e.getMessage()))
                .then();
    }

    @Override
    public Mono<EnrichedStockData> runPipelineForStock(String symbol) {
        log.info("🚀 Running pipeline for stock: {}", symbol);

        return stockAnalysisProvider.fetchStockData(symbol)
                .flatMap(enrichedData -> {
                    // Log calculated values before saving
                    Stock stock = enrichedData.getStock();
                    if (stock != null && stock.getCalculatedValues() != null) {
                        log.info("📊 BEFORE SAVE - Stock: {}, Graham Fair Value: {}, Margin of Safety: {}%",
                                symbol,
                                stock.getCalculatedValues().getGrahamFairValue(),
                                stock.getCalculatedValues().getMarginOfSafety());
                    }
                    return saveOrUpdateStock(stock)
                            .thenReturn(enrichedData);
                })
                .doOnSuccess(enrichedData -> {
                    // Log after save
                    Stock stock = enrichedData.getStock();
                    if (stock != null && stock.getCalculatedValues() != null) {
                        log.info("✅ AFTER SAVE - Stock: {}, Graham Fair Value: {}, Margin of Safety: {}%",
                                symbol,
                                stock.getCalculatedValues().getGrahamFairValue(),
                                stock.getCalculatedValues().getMarginOfSafety());
                    }
                    log.info("✅ Successfully processed stock: {}", symbol);
                })
                .doOnError(e -> log.error("❌ Failed to process stock {}: {}", symbol, e.getMessage()));
    }

    @Override
    public String getActiveProviderName() {
            return Constants.PROVIDER_STOCKANALYSIS;
    }

    /**
     * Save or update a single stock - UPSERT operation
     * Prevents duplicate key violations by updating existing records
     */
    private Mono<Stock> saveOrUpdateStock(Stock newStock) {
        if (newStock == null) {
            return Mono.empty();
        }

        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        newStock.setLastUpdate(now);
        newStock.setUpdatedAt(now);

        String symbol = newStock.getSymbol();
        String exchange = newStock.getExchange();

        log.debug("🔄 Processing stock: {} on exchange: {}", symbol, exchange);

        // Check if stock already exists
        return Mono.fromCallable(() -> stockRepository.findBySymbolAndExchange(symbol, exchange))
                .flatMap(optionalStock -> {
                    if (optionalStock.isPresent()) {
                        // UPDATE existing stock
                        Stock existingStock = optionalStock.get();
                        log.info("📝 Updating existing stock: {} (ID: {})", symbol, existingStock.getId());

                        // Preserve the ID and creation date
                        newStock.setId(existingStock.getId());
                        newStock.setCreatedAt(existingStock.getCreatedAt());

                        // Log calculated values for debugging
                        if (newStock.getCalculatedValues() != null) {
                            log.info("📊 UPDATING with Graham Fair Value: {}, Margin of Safety: {}%",
                                    newStock.getCalculatedValues().getGrahamFairValue(),
                                    newStock.getCalculatedValues().getMarginOfSafety());
                        }

                        return Mono.fromCallable(() -> stockRepository.save(newStock));
                    } else {
                        // INSERT new stock
                        log.info("📝 Inserting new stock: {}", symbol);
                        newStock.setCreatedAt(now);

                        // Log calculated values for debugging
                        if (newStock.getCalculatedValues() != null) {
                            log.info("📊 INSERTING with Graham Fair Value: {}, Margin of Safety: {}%",
                                    newStock.getCalculatedValues().getGrahamFairValue(),
                                    newStock.getCalculatedValues().getMarginOfSafety());
                        }

                        return Mono.fromCallable(() -> stockRepository.save(newStock));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(savedStock -> {
                    // Verify the saved values
                    if (savedStock.getCalculatedValues() != null) {
                        log.info("✅ SAVED - Stock: {}, Graham Fair Value: {}, Margin of Safety: {}%",
                                savedStock.getSymbol(),
                                savedStock.getCalculatedValues().getGrahamFairValue(),
                                savedStock.getCalculatedValues().getMarginOfSafety());
                    }
                });
    }

    /**
     * Save all stocks to database with UPSERT logic
     */
    private Mono<Void> saveAllToDatabase(List<EnrichedStockData> stocks) {
        log.info("💾 Saving {} stocks to database (UPSERT mode)", stocks.size());

        return Flux.fromIterable(stocks)
                .parallel(10)
                .runOn(Schedulers.boundedElastic())
                .flatMap(enrichedData -> {
                    if (enrichedData.getStock() != null) {
                        Stock stock = enrichedData.getStock();
                        // Log before saving
                        if (stock.getCalculatedValues() != null) {
                            log.debug("📊 Stock: {} - Graham: {}, MOS: {}%",
                                    stock.getSymbol(),
                                    stock.getCalculatedValues().getGrahamFairValue(),
                                    stock.getCalculatedValues().getMarginOfSafety());
                        }
                        return saveOrUpdateStock(stock)
                                .onErrorResume(e -> {
                                    log.error("Failed to save stock {}: {}",
                                            stock.getSymbol(), e.getMessage());
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