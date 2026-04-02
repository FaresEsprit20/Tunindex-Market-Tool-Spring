package com.tunindex.market_tool.domain.services.enricher;

import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.entities.Stock;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface DataEnricherService {

    /**
     * Enrich stock data with calculated values (BVPS, Fair Value, Margin of Safety)
     * Maps to Python: services/enricher.py - enrich(stock)
     */
    Mono<EnrichedStockData> enrich(Stock stock);

    /**
     * Calculate Book Value Per Share from Total Equity and Shares Outstanding
     */
    BigDecimal calculateBvps(Stock stock);

}