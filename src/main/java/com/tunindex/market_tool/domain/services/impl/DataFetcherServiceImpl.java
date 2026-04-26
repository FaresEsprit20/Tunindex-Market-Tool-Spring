package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.core.webscraping.RateLimiterManager;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.services.fetcher.DataFetcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
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
    private final WebClient webClient;

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

        // Sequential fetch with delays to avoid rate limiting
        return fetchAndSetWithWebClient(mainUrl, rawData::setMainPageHtml)
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithWebClient(balanceUrl, rawData::setBalanceSheetHtml))
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithWebClient(incomeUrl, rawData::setIncomeStatementHtml))
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchAndSetWithWebClient(financialUrl, rawData::setFinancialSummaryHtml))
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
        return fetchWithRateLimit(url);
    }

    @Override
    public Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs) {
        return fetchWithRateLimit(url)
                .retry(retries)
                .onErrorResume(e -> {
                    log.warn("Retry {} failed for {}: {}", retries, url, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Fetch URL with rate limiting using WebClient directly
     */
    private Mono<String> fetchWithRateLimit(String url) {
        return rateLimiterManager.waitForSlot()
                .then(fetchDirect(url))
                .doOnNext(html -> {
                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for URL: {}", url);
                    } else {
                        boolean hasNextData = html.contains("__NEXT_DATA__");
                        log.debug("Fetched {} (length: {}, has __NEXT_DATA__: {})", url, html.length(), hasNextData);
                    }
                });
    }

    /**
     * Direct WebClient fetch with realistic browser headers
     */
    private Mono<String> fetchDirect(String url) {
        log.debug("Fetching URL directly with WebClient: {}", url);

        return webClient.get()
                .uri(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30));
    }

    /**
     * Fetch URL and set result using consumer
     */
    private Mono<Void> fetchAndSetWithWebClient(String url, Consumer<String> setter) {
        return fetchWithRateLimit(url)
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