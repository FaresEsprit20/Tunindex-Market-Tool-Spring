package com.tunindex.market_tool.collector.services.impl;

import com.tunindex.market_tool.collector.providers.ilboursa.IlBoursaQuoteProvider;
import com.tunindex.market_tool.collector.providers.stockanalysis.StockAnalysisProvider;
import com.tunindex.market_tool.collector.services.status.PipelineStatusService;
import com.tunindex.market_tool.common.dto.pipeline.PipelinePhase;
import com.tunindex.market_tool.common.utils.constants.Constants;
import com.tunindex.market_tool.collector.dto.investingcom.EnrichedStockData;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import com.tunindex.market_tool.collector.services.orchestrator.DataOrchestrator;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataOrchestratorImpl implements DataOrchestrator {

    private final StockRepository stockRepository;
    private final StockAnalysisProvider stockAnalysisProvider;
    private final IlBoursaQuoteProvider ilBoursaQuoteProvider;
    private final PipelineStatusService pipelineStatus;

    @Override
    public Mono<Void> runPipeline() {
        log.info("🚀 Running pipeline with provider: {}", getActiveProviderName());
        pipelineStatus.start(Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS.size());

        return stockAnalysisProvider.fetchAllStocks()
                .collectList()
                .flatMap(this::saveAllToDatabase)
                .doOnSuccess(v -> {
                    log.info("✅ Pipeline completed successfully");
                    pipelineStatus.finish(true);
                })
                .doOnError(e -> {
                    log.error("❌ Pipeline failed: {}", e.getMessage());
                    pipelineStatus.finish(false);
                })
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
     * stockanalysis.com's "current" price/volume was found to lag a full
     * trading day for this market (confirmed by comparing it against
     * ilboursa.com's live quote and our own scraped history for the same
     * date). ilboursa's plain quote page has no such lag, so it overrides
     * lastPrice/prevClose/dayHigh/dayLow/volume here — everything else
     * (fundamentals, 52-week range, ratios) still comes from
     * stockanalysis.com. A failed or empty ilboursa fetch leaves the
     * original stockanalysis.com values untouched rather than blocking the
     * save.
     */
    private Mono<Stock> applyLiveQuote(Stock stock) {
        return ilBoursaQuoteProvider.fetchQuote(stock.getSymbol())
                .doOnNext(quote -> {
                    if (quote.lastPrice() == null) {
                        return;
                    }
                    if (stock.getPriceData() != null) {
                        stock.getPriceData().setLastPrice(quote.lastPrice());
                        if (quote.prevClose() != null) stock.getPriceData().setPrevClose(quote.prevClose());
                        if (quote.dayHigh() != null) stock.getPriceData().setDayHigh(quote.dayHigh());
                        if (quote.dayLow() != null) stock.getPriceData().setDayLow(quote.dayLow());
                        // Stamped only on success. A symbol whose page 404s
                        // keeps the fundamentals provider's lagging price, and
                        // this field is what stops that being presented as today.
                        stock.getPriceData().setLiveQuoteAt(LocalDateTime.now());
                    }
                    if (stock.getVolumeData() != null && quote.volume() != null) {
                        stock.getVolumeData().setVolume(quote.volume());
                    }
                    log.debug("📡 Applied live ilboursa quote for {}: price={}", stock.getSymbol(), quote.lastPrice());
                })
                .thenReturn(stock)
                .defaultIfEmpty(stock);
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
                        String symbol = stock.getSymbol();
                        String threadName = Thread.currentThread().getName();

                        // Log before saving
                        if (stock.getCalculatedValues() != null) {
                            log.debug("📊 Stock: {} - Graham: {}, MOS: {}%",
                                    symbol,
                                    stock.getCalculatedValues().getGrahamFairValue(),
                                    stock.getCalculatedValues().getMarginOfSafety());
                        }

                        pipelineStatus.workerStarted(threadName, symbol, PipelinePhase.SAVING);

                        return applyLiveQuote(stock)
                                .flatMap(this::saveOrUpdateStock)
                                .doOnSuccess(saved -> pipelineStatus.workerFinished(threadName, symbol, PipelinePhase.SAVING, true))
                                .onErrorResume(e -> {
                                    log.error("Failed to save stock {}: {}", symbol, e.getMessage());
                                    pipelineStatus.workerFinished(threadName, symbol, PipelinePhase.SAVING, false);
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