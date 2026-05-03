package com.tunindex.market_tool.api.services.enricher;

import com.tunindex.market_tool.api.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.api.entities.Stock;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface DataEnricherService {
    Mono<EnrichedStockData> enrich(Stock stock);
    BigDecimal calculateBvps(Stock stock);
}