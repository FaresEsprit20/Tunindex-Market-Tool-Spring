package com.tunindex.market_tool.domain.services.fetcher;

import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import reactor.core.publisher.Mono;

public interface DataFetcherService {

    /**
     * Fetch raw HTML data for a stock from Investing.com
     * Includes retries, backoff, User-Agent rotation, and optional proxy
     */
    Mono<RawStockData> fetchStockData(String symbol);

    /**
     * Fetch raw HTML from a URL with retry logic
     */
    Mono<String> fetchUrl(String url, boolean useProxy);

    /**
     * Fetch raw HTML with custom retry configuration
     */
    Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs);
}