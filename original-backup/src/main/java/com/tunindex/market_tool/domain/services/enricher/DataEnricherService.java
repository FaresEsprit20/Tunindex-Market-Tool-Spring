package com.tunindex.market_tool.domain.services.enricher;

import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.entities.Stock;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface DataEnricherService {
    Mono<EnrichedStockData> enrich(Stock stock);
    BigDecimal calculateBvps(Stock stock);
}