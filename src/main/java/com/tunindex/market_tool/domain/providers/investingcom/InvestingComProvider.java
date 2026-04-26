package com.tunindex.market_tool.domain.providers.investingcom;

import com.tunindex.market_tool.core.config.selenium.ChromeDriverService;
import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.providers.base.MarketDataProvider;
import com.tunindex.market_tool.domain.services.enricher.DataEnricherService;
import com.tunindex.market_tool.domain.services.normalizer.DataNormalizerService;
import com.tunindex.market_tool.domain.services.parser.DataParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collections;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvestingComProvider implements MarketDataProvider {

    private final DataParserService dataParserService;
    private final DataNormalizerService normalizer;
    private final DataEnricherService enricher;

    // CRITICAL: Dedicated single-thread scheduler for Selenium
    // ChromeDriver is NOT thread-safe and must always be accessed from the same thread
    private static final Scheduler SELENIUM_SCHEDULER = Schedulers.newSingle("selenium-thread", true);

    private static final int DELAY_MIN_MS = 3000;
    private static final int DELAY_MAX_MS = 8000;
    private final Random random = new Random();

    @Override
    public String getProviderName() {
        return Constants.PROVIDER_INVESTINGCOM;
    }

    @Override
    public Mono<EnrichedStockData> fetchStockData(String symbol) {
        log.info("Fetching data for symbol: {}", symbol);

        Constants.StockInfo stockInfo = Constants.TUNISIAN_STOCKS.get(symbol);
        if (stockInfo == null) {
            return Mono.error(new DataFetchException(
                    ErrorCodes.PROVIDER_NOT_FOUND,
                    getProviderName(),
                    symbol,
                    "Stock not found in configuration",
                    Collections.singletonList("Symbol: " + symbol)
            ));
        }

        // Create a NEW ChromeDriver for each stock, pinned to single thread
        return Mono.fromCallable(() -> {
                    ChromeDriverService driverService = new ChromeDriverService();
                    driverService.init();
                    return driverService;
                })
                .subscribeOn(SELENIUM_SCHEDULER)  // CRITICAL: pin to one thread
                .flatMap(driverService -> fetchAllPages(driverService, symbol, stockInfo)
                        .doFinally(signalType -> {
                            // Always cleanup the driver
                            try {
                                driverService.cleanup();
                            } catch (Exception e) {
                                log.warn("Error cleaning up driver for {}: {}", symbol, e.getMessage());
                            }
                        }));
    }

    // SEQUENTIAL fetching - NOT parallel!
    // One ChromeDriver cannot handle multiple simultaneous driver.get() calls
    private Mono<EnrichedStockData> fetchAllPages(ChromeDriverService driverService,
                                                  String symbol,
                                                  Constants.StockInfo stockInfo) {
        String baseUrl = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();

        // Sequential flatMap chaining instead of Mono.zip
        return fetchPage(driverService, baseUrl, symbol, "main")
                .delayElement(Duration.ofMillis(randomDelay()))
                .flatMap(mainHtml ->
                        fetchPage(driverService, baseUrl + Constants.INVESTINGCOM_BALANCE_SHEET, symbol, "balance")
                                .delayElement(Duration.ofMillis(randomDelay()))
                                .flatMap(balanceHtml ->
                                        fetchPage(driverService, baseUrl + Constants.INVESTINGCOM_INCOME_STATEMENT, symbol, "income")
                                                .delayElement(Duration.ofMillis(randomDelay()))
                                                .flatMap(incomeHtml ->
                                                        fetchPage(driverService, baseUrl + Constants.INVESTINGCOM_FINANCIAL_SUMMARY, symbol, "financial")
                                                                .map(summaryHtml -> {
                                                                    RawStockData rawData = new RawStockData();
                                                                    rawData.setSymbol(symbol);
                                                                    rawData.setStockInfo(stockInfo);
                                                                    rawData.setMainPageHtml(mainHtml);
                                                                    rawData.setBalanceSheetHtml(balanceHtml);
                                                                    rawData.setIncomeStatementHtml(incomeHtml);
                                                                    rawData.setFinancialSummaryHtml(summaryHtml);
                                                                    return rawData;
                                                                })
                                                )
                                )
                )
                .map(dataParserService::parseToNormalized)
                .map(normalizer::toEntity)
                .flatMap(enricher::enrich)
                .doOnSuccess(data -> log.info("Successfully fetched and enriched data for {}", symbol))
                .doOnError(error -> log.error("Failed to fetch data for {}: {}", symbol, error.getMessage()));
    }

    private Mono<String> fetchPage(ChromeDriverService driverService,
                                   String url,
                                   String symbol,
                                   String pageType) {
        return Mono.fromCallable(() -> {
                    log.debug("Fetching {} page for {}: {}", pageType, symbol, url);
                    String html = driverService.fetchPage(url);

                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for {} page of {}", pageType, symbol);
                        return "";
                    }

                    if (html.contains("Access Denied") || html.contains("403") || html.contains("captcha")) {
                        log.warn("Access blocked for {} page of {}", pageType, symbol);
                        return "";
                    }

                    if (html.contains("__NEXT_DATA__")) {
                        log.info("✅ Successfully fetched full {} page for {}", pageType, url);
                    } else {
                        log.warn("Fetched simplified {} page (missing __NEXT_DATA__) for {}", pageType, url);
                    }

                    return html;
                })
                .subscribeOn(SELENIUM_SCHEDULER)  // Same single thread
                .timeout(Duration.ofSeconds(60))
                .onErrorResume(e -> {
                    log.warn("Failed to fetch {} page for {}: {}", pageType, symbol, e.getMessage());
                    return Mono.just("");
                });
    }

    @Override
    public Flux<EnrichedStockData> fetchAllStocks() {
        log.info("Fetching all stocks from Investing.com");

        return Flux.fromIterable(Constants.TUNISIAN_STOCKS.entrySet())
                .index()
                .flatMap(tuple -> {
                    long index = tuple.getT1();
                    var entry = tuple.getT2();

                    // Add delay between stocks to avoid overwhelming the system
                    return Mono.delay(Duration.ofSeconds(index * 10))
                            .then(fetchStockData(entry.getKey()))
                            .onErrorResume(error -> {
                                log.error("Failed to fetch {}: {}", entry.getKey(), error.getMessage());
                                return Mono.empty();
                            });
                }, 1)  // Process one stock at a time
                .doOnComplete(() -> log.info("Completed fetching all stocks"));
    }

    @Override
    public boolean supports(String symbol) {
        return Constants.TUNISIAN_STOCKS.containsKey(symbol);
    }

    private long randomDelay() {
        return DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS);
    }
}