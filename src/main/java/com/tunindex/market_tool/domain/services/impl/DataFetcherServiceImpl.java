package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.config.flaresolverclient.FlareSolverClient;
import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.core.webscraping.RateLimiterManager;
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

    private final FlareSolverClient flareSolverClient;
    private final RateLimiterManager rateLimiterManager;

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
        String proxyUrl = useProxy ? getProxyUrl() : null;

        // Sequential fetch with delays to avoid rate limiting
        return fetchAndSetWithFlare(mainUrl, proxyUrl, rawData::setMainPageHtml)
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithFlare(balanceUrl, proxyUrl, rawData::setBalanceSheetHtml))
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithFlare(incomeUrl, proxyUrl, rawData::setIncomeStatementHtml))
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithFlare(financialUrl, proxyUrl, rawData::setFinancialSummaryHtml))
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
        String proxyUrl = useProxy ? getProxyUrl() : null;
        return fetchWithRateLimit(url, proxyUrl);
    }

    @Override
    public Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs) {
        String proxyUrl = useProxy ? getProxyUrl() : null;

        return fetchWithRateLimit(url, proxyUrl)
                .retry(retries)
                .onErrorResume(e -> {
                    log.warn("Retry {} failed for {}: {}", retries, url, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Fetch URL with rate limiting
     */
    private Mono<String> fetchWithRateLimit(String url, String proxyUrl) {
        return rateLimiterManager.waitForSlot()
                .then(flareSolverClient.fetch(url, proxyUrl))
                .doOnNext(html -> {
                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for URL: {}", url);
                    } else {
                        log.debug("Successfully fetched {} (length: {})", url, html.length());
                    }
                });
    }

    /**
     * Fetch URL and set result using consumer
     */
    private Mono<Void> fetchAndSetWithFlare(String url, String proxyUrl, Consumer<String> setter) {
        return fetchWithRateLimit(url, proxyUrl)
                .doOnNext(setter)
                .onErrorResume(e -> {
                    log.error("Failed to fetch URL: {} - {}", url, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Get proxy URL from ProxyManager (you can implement this method)
     * For now, returns null to use no proxy
     */
    private String getProxyUrl() {
        // TODO: Implement proxy rotation
        // You can get a proxy from ProxyManager here
        // Example: return proxyManager.getRandomProxy();
        return null;
    }

    /**
     * Random delay to mimic human behavior
     */
    private long randomDelay() {
        return DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS);
    }
}