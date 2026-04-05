package com.tunindex.market_tool.domain.providers.investingcom;

import com.tunindex.market_tool.core.config.flaresolverclient.FlareSolverClient;
import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.core.webscraping.ProxyManager;
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

    private final FlareSolverClient flareSolverClient;
    private final RateLimiterManager rateLimiterManager;
    private final ProxyManager proxyManager;
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

        // Get rotating proxy for this request
        String proxyUrl = getRotatingProxy();

        return fetchMainPage(symbol, stockInfo, proxyUrl)
                .delayElement(Duration.ofMillis(randomDelay()))
                .flatMap(rawData -> fetchBalanceSheet(symbol, stockInfo, rawData, proxyUrl))
                .delayElement(Duration.ofMillis(randomDelay()))
                .flatMap(rawData -> fetchIncomeStatement(symbol, stockInfo, rawData, proxyUrl))
                .delayElement(Duration.ofMillis(randomDelay()))
                .flatMap(rawData -> fetchFinancialRatios(symbol, stockInfo, rawData, proxyUrl))
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
                .parallel()
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

    private Mono<RawStockData> fetchMainPage(String symbol, Constants.StockInfo stockInfo, String proxyUrl) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        log.debug("Fetching main page via FlareSolverr: {} (proxy: {})", url, proxyUrl != null ? "enabled" : "none");

        return fetchWithRateLimit(url, proxyUrl)
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

    private Mono<RawStockData> fetchBalanceSheet(String symbol, Constants.StockInfo stockInfo, RawStockData rawData, String proxyUrl) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_BALANCE_SHEET;
        log.debug("Fetching balance sheet via FlareSolverr: {}", url);

        return fetchWithRateLimit(url, proxyUrl)
                .map(html -> {
                    rawData.setBalanceSheetHtml(html);
                    return rawData;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch balance sheet for {}: {}", symbol, e.getMessage());
                    return Mono.just(rawData);
                });
    }

    private Mono<RawStockData> fetchIncomeStatement(String symbol, Constants.StockInfo stockInfo, RawStockData rawData, String proxyUrl) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_INCOME_STATEMENT;
        log.debug("Fetching income statement via FlareSolverr: {}", url);

        return fetchWithRateLimit(url, proxyUrl)
                .map(html -> {
                    rawData.setIncomeStatementHtml(html);
                    return rawData;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch income statement for {}: {}", symbol, e.getMessage());
                    return Mono.just(rawData);
                });
    }

    private Mono<RawStockData> fetchFinancialRatios(String symbol, Constants.StockInfo stockInfo, RawStockData rawData, String proxyUrl) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl() + Constants.INVESTINGCOM_FINANCIAL_SUMMARY;
        log.debug("Fetching financial summary via FlareSolverr: {}", url);

        return fetchWithRateLimit(url, proxyUrl)
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
     * Fetch URL with rate limiting
     */
    private Mono<String> fetchWithRateLimit(String url, String proxyUrl) {
        return rateLimiterManager.waitForSlot()
                .then(flareSolverClient.fetch(url, proxyUrl))
                .doOnNext(html -> {
                    if (html == null || html.isEmpty()) {
                        log.warn("Empty response for URL: {}", url);
                    }
                });
    }

    /**
     * Get rotating proxy for each request
     * This prevents IP blocking by using different proxies
     */
    private String getRotatingProxy() {
        if (Constants.USE_PROXY && proxyManager.hasProxies()) {
            String proxy = proxyManager.getProxyUrl();
            log.debug("Using rotating proxy for request");
            return proxy;
        }
        return null;
    }

    private long randomDelay() {
        return DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS);
    }
}