package com.tunindex.market_tool.collector.services.fetcher;

import com.tunindex.market_tool.common.dto.providers.investingcom.RawStockData;
import reactor.core.publisher.Mono;

public interface DataFetcherService {
    Mono<RawStockData> fetchStockData(String symbol);
    Mono<String> fetchUrl(String url, boolean useProxy);
    Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs);
}