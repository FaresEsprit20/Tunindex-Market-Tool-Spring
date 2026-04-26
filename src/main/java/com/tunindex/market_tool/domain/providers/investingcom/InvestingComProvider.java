package com.tunindex.market_tool.domain.providers.investingcom;

import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.core.webscraping.RateLimiterManager;
import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.providers.base.MarketDataProvider;
import com.tunindex.market_tool.domain.services.enricher.DataEnricherService;
import com.tunindex.market_tool.domain.services.normalizer.DataNormalizerService;
import com.tunindex.market_tool.domain.services.parser.DataParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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

    private final RateLimiterManager rateLimiterManager;
    private final DataParserService dataParserService;
    private final DataNormalizerService normalizer;
    private final DataEnricherService enricher;
    private final WebClient webClient;

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

    @Override
    public Flux<EnrichedStockData> fetchAllStocks() {
        log.info("Fetching all stocks from Investing.com");

        return Flux.fromIterable(Constants.TUNISIAN_STOCKS.entrySet())
                .parallel(3)  // Limit parallel calls
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

    private Mono<RawStockData> fetchMainPage(String symbol, Constants.StockInfo stockInfo) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        log.debug("Fetching main page: {}", url);

        return fetchWithRateLimit(url)
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
        log.debug("Fetching balance sheet: {}", url);

        return fetchWithRateLimit(url)
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
        log.debug("Fetching income statement: {}", url);

        return fetchWithRateLimit(url)
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
        log.debug("Fetching financial summary: {}", url);

        return fetchWithRateLimit(url)
                .map(html -> {
                    rawData.setFinancialSummaryHtml(html);
                    return rawData;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch financial summary for {}: {}", symbol, e.getMessage());
                    return Mono.just(rawData);
                });
    }

    /**
     * Fetch URL with rate limiting and proper headers
     */
    private Mono<String> fetchWithRateLimit(String url) {
        return rateLimiterManager.waitForSlot()
                .then(fetchDirect(url))
                .doOnNext(html -> {
                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for URL: {}", url);
                    } else if (html.contains("__NEXT_DATA__")) {
                        log.debug("Successfully fetched full page for {}", url);
                    } else {
                        log.warn("Fetched simplified page (missing __NEXT_DATA__) for {}", url);
                    }
                });
    }

    /**
     * Direct WebClient fetch with realistic browser headers
     */
    private Mono<String> fetchDirect(String url) {
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

    private long randomDelay() {
        return DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS);
    }
}