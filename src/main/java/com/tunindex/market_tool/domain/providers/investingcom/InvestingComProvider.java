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
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collections;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvestingComProvider implements MarketDataProvider {

    private final ChromeDriverService chromeDriverService;
    private final DataParserService dataParserService;
    private final DataNormalizerService normalizer;
    private final DataEnricherService enricher;

    private static final int DELAY_MIN_MS = 2000;
    private static final int DELAY_MAX_MS = 5000;
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

        return fetchMainPage(symbol, stockInfo)
                .delayElement(Duration.ofMillis(randomDelay()))
                .flatMap(rawData -> fetchBalanceSheet(symbol, stockInfo, rawData))
                .delayElement(Duration.ofMillis(randomDelay()))
                .flatMap(rawData -> fetchIncomeStatement(symbol, stockInfo, rawData))
                .delayElement(Duration.ofMillis(randomDelay()))
                .flatMap(rawData -> fetchFinancialRatios(symbol, stockInfo, rawData))
                .map(dataParserService::parseToNormalized)
                .map(normalizer::toEntity)
                .flatMap(enricher::enrich)
                .doOnSuccess(data -> log.info("Successfully fetched and enriched data for {}", symbol))
                .doOnError(error -> log.error("Failed to fetch data for {}: {}", symbol, error.getMessage()));
    }

    private Mono<RawStockData> fetchMainPage(String symbol, Constants.StockInfo stockInfo) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        log.debug("Fetching main page via Selenium: {}", url);

        return fetchWithSelenium(url, symbol)
                .map(html -> {
                    RawStockData rawData = new RawStockData();
                    rawData.setSymbol(symbol);
                    rawData.setStockInfo(stockInfo);
                    rawData.setMainPageHtml(html);
                    return rawData;
                })
                .onErrorMap(e -> new DataFetchException(
                        ErrorCodes.DATA_FETCH_FAILED,
                        getProviderName(),
                        symbol,
                        "Failed to fetch main page: " + e.getMessage(),
                        Collections.singletonList(url)
                ));
    }

    private Mono<RawStockData> fetchBalanceSheet(String symbol, Constants.StockInfo stockInfo, RawStockData rawData) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_BALANCE_SHEET;
        log.debug("Fetching balance sheet via Selenium: {}", url);

        return fetchWithSelenium(url, symbol)
                .map(html -> {
                    rawData.setBalanceSheetHtml(html);
                    return rawData;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch balance sheet for {}: {}", symbol, e.getMessage());
                    return Mono.just(rawData);
                });
    }

    private Mono<RawStockData> fetchIncomeStatement(String symbol, Constants.StockInfo stockInfo, RawStockData rawData) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_INCOME_STATEMENT;
        log.debug("Fetching income statement via Selenium: {}", url);

        return fetchWithSelenium(url, symbol)
                .map(html -> {
                    rawData.setIncomeStatementHtml(html);
                    return rawData;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch income statement for {}: {}", symbol, e.getMessage());
                    return Mono.just(rawData);
                });
    }

    private Mono<RawStockData> fetchFinancialRatios(String symbol, Constants.StockInfo stockInfo, RawStockData rawData) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_FINANCIAL_SUMMARY;
        log.debug("Fetching financial summary via Selenium: {}", url);

        return fetchWithSelenium(url, symbol)
                .map(html -> {
                    rawData.setFinancialSummaryHtml(html);
                    return rawData;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch financial summary for {}: {}", symbol, e.getMessage());
                    return Mono.just(rawData);
                });
    }

    private Mono<String> fetchWithSelenium(String url, String symbol) {
        return Mono.fromCallable(() -> chromeDriverService.fetchPage(url))
                .doOnNext(html -> {
                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for URL: {}", url);
                    } else if (html.contains("__NEXT_DATA__")) {
                        log.info("✅ Successfully fetched full page for {}", url);
                    } else {
                        log.warn("Fetched simplified page (missing __NEXT_DATA__) for {}", url);
                    }
                });
    }

    @Override
    public Flux<EnrichedStockData> fetchAllStocks() {
        log.info("Fetching all stocks from Investing.com");

        return Flux.fromIterable(Constants.TUNISIAN_STOCKS.entrySet())
                .parallel(1)  // Selenium needs sequential execution to avoid browser conflicts
                .runOn(Schedulers.boundedElastic())
                .flatMap(entry -> fetchStockData(entry.getKey())
                        .onErrorResume(error -> {
                            log.error("Failed to fetch {}: {}", entry.getKey(), error.getMessage());
                            return Mono.empty();
                        }))
                .sequential()
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