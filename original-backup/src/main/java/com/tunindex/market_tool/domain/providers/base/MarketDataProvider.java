package com.tunindex.market_tool.domain.providers.base;

import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MarketDataProvider {

    String getProviderName();

    Mono<EnrichedStockData> fetchStockData(String symbol);

    Flux<EnrichedStockData> fetchAllStocks();

    boolean supports(String symbol);
}