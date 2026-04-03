package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.core.webscraping.ProxyManager;
import com.tunindex.market_tool.core.webscraping.RateLimiterManager;
import com.tunindex.market_tool.core.webscraping.RetryManager;
import com.tunindex.market_tool.core.webscraping.UserAgentManager;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.services.fetcher.DataFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataFetcherServiceImpl implements DataFetcherService {

    private final WebClient webClient;
    private final UserAgentManager userAgentManager;
    private final ProxyManager proxyManager;
    private final RateLimiterManager rateLimiterManager;
    private final RetryManager retryManager;

    @Override
    public Mono<RawStockData> fetchStockData(String symbol) {
        Constants.StockInfo stockInfo = Constants.TUNISIAN_STOCKS.get(symbol);
        if (stockInfo == null) {
            return Mono.error(new DataFetchException(
                    ErrorCodes.PROVIDER_NOT_FOUND,
                    Constants.PROVIDER_INVESTINGCOM,
                    symbol,
                    "Stock not found in configuration",
                    Collections.singletonList("Symbol: " + symbol)
            ));
        }

        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        String mainUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        String balanceUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_BALANCE_SHEET;
        String incomeUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_INCOME_STATEMENT;
        String financialUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_FINANCIAL_SUMMARY;

        // Fetch main page first, then chain other requests
        return fetchAndSet(mainUrl, false, rawData::setMainPageHtml)
                .then(fetchAndSet(balanceUrl, false, rawData::setBalanceSheetHtml))
                .then(fetchAndSet(incomeUrl, false, rawData::setIncomeStatementHtml))
                .then(fetchAndSet(financialUrl, false, rawData::setFinancialSummaryHtml))
                .thenReturn(rawData)
                .onErrorMap(e -> new DataFetchException(
                        ErrorCodes.DATA_FETCH_FAILED,
                        Constants.PROVIDER_INVESTINGCOM,
                        symbol,
                        "Failed to fetch stock data: " + e.getMessage(),
                        Collections.singletonList(e.getMessage())
                ));
    }

    @Override
    public Mono<String> fetchUrl(String url, boolean useProxy) {
        return fetchUrlWithRetry(url, useProxy, 3, 2000);
    }

    @Override
    public Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs) {
        log.debug("Fetching URL: {} (useProxy: {}, retries: {})", url, useProxy, retries);

        // Apply rate limiting and build request
        return rateLimiterManager.waitForSlot()
                .then(Mono.defer(() -> buildRequest(url, useProxy)))
                .retryWhen(Retry.backoff(retries, Duration.ofMillis(backoffMs))
                        .maxBackoff(Duration.ofMillis(backoffMs * 4))
                        .doBeforeRetry(retrySignal -> {
                            long currentDelay = (long) (backoffMs * Math.pow(2, retrySignal.totalRetries()));
                            log.warn("Retry {} for URL: {} after {}ms",
                                    retrySignal.totalRetries() + 1,
                                    url,
                                    currentDelay);
                        }))
                .onErrorMap(e -> new DataFetchException(
                        ErrorCodes.DATA_FETCH_FAILED,
                        Constants.PROVIDER_INVESTINGCOM,
                        null,
                        "Failed to fetch URL: " + url + " - " + e.getMessage(),
                        Collections.singletonList(url)
                ));
    }

    /**
     * Helper method to fetch a URL and set the result using the provided setter
     * Returns Mono<Void> to allow chaining with then()
     */
    private Mono<Void> fetchAndSet(String url, boolean useProxy, Consumer<String> setter) {
        return fetchUrl(url, useProxy)
                .doOnNext(setter)
                .onErrorResume(e -> {
                    log.warn("Failed to fetch URL: {} - {}", url, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<String> buildRequest(String url, boolean useProxy) {
        WebClient.RequestHeadersSpec<?> request = webClient.get()
                .uri(url)
                .header("User-Agent", userAgentManager.getRandomUserAgent())
                .header("Accept", Constants.DEFAULT_ACCEPT)
                .header("Accept-Language", Constants.DEFAULT_ACCEPT_LANGUAGE)
                .header("Accept-Encoding", Constants.DEFAULT_ACCEPT_ENCODING)
                .header("Connection", Constants.DEFAULT_CONNECTION);

        return request.retrieve()
                .bodyToMono(String.class)
                .handle((response, sink) -> {
                    if (response == null || response.isEmpty()) {
                        sink.error(new DataFetchException(
                                ErrorCodes.DATA_FETCH_EMPTY_RESPONSE,
                                Constants.PROVIDER_INVESTINGCOM,
                                null,
                                "Empty response from URL: " + url,
                                Collections.singletonList(url)
                        ));
                    } else {
                        sink.next(response);
                    }
                });
    }
}