package com.tunindex.market_tool.collector.providers.base;

import com.tunindex.market_tool.common.dto.providers.investingcom.EnrichedStockData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MarketDataProvider {

    String getProviderName();

    Mono<EnrichedStockData> fetchStockData(String symbol);

    Flux<EnrichedStockData> fetchAllStocks();

    boolean supports(String symbol);
}