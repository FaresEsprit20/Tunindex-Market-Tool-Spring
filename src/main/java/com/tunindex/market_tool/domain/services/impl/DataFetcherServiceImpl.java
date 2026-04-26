package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.config.selenium.ChromeDriverService;
import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
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

    // Remove ChromeDriverService injection - we'll create drivers manually
    // private final ChromeDriverService chromeDriverService;

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

        // Create a new ChromeDriver for each stock
        return Mono.fromCallable(() -> {
                    ChromeDriverService driverService = new ChromeDriverService();
                    driverService.init();
                    return driverService;
                })
                .flatMap(driverService -> fetchAllPages(driverService, symbol, stockInfo)
                        .doFinally(signalType -> {
                            try {
                                driverService.cleanup();
                            } catch (Exception e) {
                                log.warn("Error cleaning up driver for {}: {}", symbol, e.getMessage());
                            }
                        }));
    }

    private Mono<RawStockData> fetchAllPages(ChromeDriverService driverService, String symbol, Constants.StockInfo stockInfo) {
        String mainUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        String balanceUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_BALANCE_SHEET;
        String incomeUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_INCOME_STATEMENT;
        String financialUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_FINANCIAL_SUMMARY;

        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        return fetchPage(driverService, mainUrl, symbol, "main")
                .doOnNext(rawData::setMainPageHtml)
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchPage(driverService, balanceUrl, symbol, "balance"))
                .doOnNext(rawData::setBalanceSheetHtml)
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchPage(driverService, incomeUrl, symbol, "income"))
                .doOnNext(rawData::setIncomeStatementHtml)
                .then(Mono.delay(Duration.ofMillis(randomDelay())))
                .then(fetchPage(driverService, financialUrl, symbol, "financial"))
                .doOnNext(rawData::setFinancialSummaryHtml)
                .thenReturn(rawData);
    }

    private Mono<String> fetchPage(ChromeDriverService driverService, String url, String symbol, String pageType) {
        return Mono.fromCallable(() -> {
                    log.debug("Fetching {} page for {}: {}", pageType, symbol, url);
                    String html = driverService.fetchPage(url);

                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for {} page of {}", pageType, symbol);
                        return "";
                    }

                    boolean hasNextData = html.contains("__NEXT_DATA__");
                    log.debug("Fetched {} page (length: {}, has __NEXT_DATA__: {})", pageType, html.length(), hasNextData);
                    return html;
                })
                .timeout(Duration.ofSeconds(45))
                .onErrorResume(e -> {
                    log.error("Failed to fetch {} page for {}: {}", pageType, symbol, e.getMessage());
                    return Mono.just("");
                });
    }

    @Override
    public Mono<String> fetchUrl(String url, boolean useProxy) {
        // This method is kept for compatibility but not recommended for use
        return Mono.fromCallable(() -> {
                    ChromeDriverService driverService = new ChromeDriverService();
                    driverService.init();
                    try {
                        return driverService.fetchPage(url);
                    } finally {
                        driverService.cleanup();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch URL: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs) {
        // Simple implementation without retry
        return fetchUrl(url, useProxy);
    }

    private long randomDelay() {
        return DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS);
    }
}