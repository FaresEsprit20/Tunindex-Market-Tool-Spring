package com.tunindex.market_tool.api.services.orchestrator;

import com.tunindex.market_tool.api.dto.providers.investingcom.EnrichedStockData;
import reactor.core.publisher.Mono;

public interface DataOrchestrator {

    /**
     * Run the complete data pipeline
     * Maps to Python: core/orchestrator.py - run_pipeline()
     */
    Mono<Void> runPipeline();

    /**
     * Run pipeline for a single stock
     */
    Mono<EnrichedStockData> runPipelineForStock(String symbol);

    /**
     * Get the active provider name
     */
    String getActiveProviderName();
}