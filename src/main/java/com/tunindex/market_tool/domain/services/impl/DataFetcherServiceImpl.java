package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.core.webscraping.RateLimiterManager;
import com.tunindex.market_tool.core.webscraping.StealthHttpClient;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.services.fetcher.DataFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.Random;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataFetcherServiceImpl implements DataFetcherService {

    private final RateLimiterManager rateLimiterManager;
    private final StealthHttpClient stealthHttpClient;

    private final Random random = new Random();
    private static final int DELAY_MIN_MS = 2000;
    private static final int DELAY_MAX_MS = 5000;

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

        boolean useProxy = Constants.USE_PROXY;

        // Sequential fetch with delays to avoid rate limiting
        return fetchAndSetWithStealth(mainUrl, useProxy, rawData::setMainPageHtml, symbol)
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithStealth(balanceUrl, useProxy, rawData::setBalanceSheetHtml, symbol))
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithStealth(incomeUrl, useProxy, rawData::setIncomeStatementHtml, symbol))
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithStealth(financialUrl, useProxy, rawData::setFinancialSummaryHtml, symbol))
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
        return fetchWithStealth(url, useProxy, null);
    }

    @Override
    public Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs) {
        return fetchWithStealth(url, useProxy, null)
                .retry(retries)
                .onErrorResume(e -> {
                    log.warn("Retry {} failed for {}: {}", retries, url, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Fetch URL with stealth anti-detection
     */
    private Mono<String> fetchWithStealth(String url, boolean useProxy, String symbol) {
        return rateLimiterManager.waitForSlot()
                .then(stealthHttpClient.fetchWithStealth(url, useProxy, symbol))
                .cast(String.class)
                .doOnNext(html -> {
                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for URL: {}", url);
                    } else {
                        boolean hasNextData = html.contains("__NEXT_DATA__");
                        log.debug("Fetched {} (length: {}, has __NEXT_DATA__: {})", url, html.length(), hasNextData);
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch URL: {} - {}", url, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Fetch URL and set result using consumer
     */
    private Mono<Void> fetchAndSetWithStealth(String url, boolean useProxy, Consumer<String> setter, String symbol) {
        return fetchWithStealth(url, useProxy, symbol)
                .doOnNext(setter)
                .onErrorResume(e -> {
                    log.error("Failed to fetch URL: {} - {}", url, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Random delay to mimic human behavior
     */
    private long randomDelay() {
        return DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS);
    }
}