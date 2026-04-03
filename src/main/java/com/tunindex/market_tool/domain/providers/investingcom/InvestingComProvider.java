package com.tunindex.market_tool.domain.providers.investingcom;

import com.tunindex.market_tool.core.exception.DataFetchException;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.domain.dto.providers.investingcom.EnrichedStockData;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.providers.base.MarketDataProvider;
import com.tunindex.market_tool.domain.repository.jpa.StockRepository;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class InvestingComProvider implements MarketDataProvider {

    private final WebClient webClient;
    private final DataParserService dataParserService;
    private final DataNormalizerService normalizer;
    private final DataEnricherService enricher;
    private final StockRepository stockRepository;

    private static final int DELAY_MIN = 1000;
    private static final int DELAY_MAX = 2000;

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
                .flatMap(enrichedData -> {
                    log.info("Saving stock data for {} to database", symbol);
                    return Mono.fromCallable(() -> {
                                Stock savedStock = stockRepository.save(enrichedData.getStock());
                                enrichedData.setStock(savedStock);
                                enrichedData.setSaved(true);
                                enrichedData.setSaveMessage("Stock data saved successfully");
                                return enrichedData;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                log.error("Failed to save stock data for {}: {}", symbol, e.getMessage());
                                enrichedData.setSaved(false);
                                enrichedData.setSaveMessage("Failed to save: " + e.getMessage());
                                return Mono.just(enrichedData);
                            });
                })
                .doOnSuccess(data -> log.info("Successfully fetched and saved data for {}", symbol))
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

    private Mono<RawStockData> fetchMainPage(String symbol, Constants.StockInfo stockInfo) {
        String url = Constants.INVESTINGCOM_BASE_URL + stockInfo.getUrl();
        log.debug("Fetching main page: {}", url);

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
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

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
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

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
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

        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .map(html -> {
                    rawData.setFinancialSummaryHtml(html);
                    return rawData;
                })
                .onErrorResume(e -> {
                    log.warn("Failed to fetch financial summary for {}: {}", symbol, e.getMessage());
                    return Mono.just(rawData);
                });
    }

    private long randomDelay() {
        return DELAY_MIN + (long) (Math.random() * (DELAY_MAX - DELAY_MIN));
    }
}