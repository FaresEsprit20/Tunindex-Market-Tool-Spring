package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
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

        // Fetch main page
        String mainUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        String balanceUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_BALANCE_SHEET;
        String incomeUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_INCOME_STATEMENT;
        String financialUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_FINANCIAL_SUMMARY;

        return fetchUrl(mainUrl, false)
                .doOnNext(rawData::setMainPageHtml)
                .flatMap(html -> fetchUrl(balanceUrl, false).doOnNext(rawData::setBalanceSheetHtml).onErrorResume(e -> Mono.just(rawData)))
                .flatMap(html -> fetchUrl(incomeUrl, false).doOnNext(rawData::setIncomeStatementHtml).onErrorResume(e -> Mono.just(rawData)))
                .flatMap(html -> fetchUrl(financialUrl, false).doOnNext(rawData::setFinancialSummaryHtml).onErrorResume(e -> Mono.just(rawData)))
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

        // Apply rate limiting
        return rateLimiterManager.waitForSlot()
                .then(buildRequest(url, useProxy))
                .retryWhen(Retry.backoff(retries, Duration.ofMillis(backoffMs))
                        .maxBackoff(Duration.ofMillis(backoffMs * 4))
                        .doBeforeRetry(retrySignal -> {
                            log.warn("Retry {} for URL: {} after {}ms",
                                    retrySignal.totalRetries() + 1,
                                    url,
                                    retrySignal.backoff().toMillis());
                        }))
                .onErrorMap(e -> new DataFetchException(
                        ErrorCodes.DATA_FETCH_FAILED,
                        Constants.PROVIDER_INVESTINGCOM,
                        null,
                        "Failed to fetch URL: " + url + " - " + e.getMessage(),
                        Collections.singletonList(url)
                ));
    }

    private Mono<String> buildRequest(String url, boolean useProxy) {
        WebClient.RequestHeadersSpec<?> request = webClient.get()
                .uri(url)
                .header("User-Agent", userAgentManager.getRandomUserAgent())
                .header("Accept", Constants.DEFAULT_ACCEPT)
                .header("Accept-Language", Constants.DEFAULT_ACCEPT_LANGUAGE)
                .header("Accept-Encoding", Constants.DEFAULT_ACCEPT_ENCODING)
                .header("Connection", Constants.DEFAULT_CONNECTION);

        // Add proxy if enabled (WebClient doesn't support proxy directly in this simple config)
        // Proxy would need to be configured at the HttpClient level

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