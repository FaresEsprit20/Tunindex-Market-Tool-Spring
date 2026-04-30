package com.tunindex.market_tool.collector.services.impl;

import com.tunindex.market_tool.common.utils.constants.Constants;
import com.tunindex.market_tool.common.dto.providers.investingcom.RawStockData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import com.tunindex.market_tool.collector.services.fetcher.DataFetcherService;

import java.time.Duration;

@Service("stockAnalysisFetcher")
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisFetcherImpl implements DataFetcherService {

    private final WebClient webClient;

    @Override
    public Mono<RawStockData> fetchStockData(String symbol) {
        log.info("Fetching StockAnalysis data for symbol: {}", symbol);

        Constants.StockInfo stockInfo = Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS.get(symbol);
        if (stockInfo == null) {
            return Mono.error(new RuntimeException("Stock not found: " + symbol));
        }

        String overviewUrl = Constants.STOCKANALYSIS_BASE_URL + symbol + "/";
        String ratiosUrl = Constants.STOCKANALYSIS_BASE_URL + symbol + "/financials/ratios/";
        String statisticsUrl = Constants.STOCKANALYSIS_BASE_URL + symbol + "/statistics/";

        return Mono.zip(
                        fetchPage(overviewUrl),
                        fetchPage(ratiosUrl),
                        fetchPage(statisticsUrl)
                )
                .map(tuple -> {
                    RawStockData rawData = new RawStockData();
                    rawData.setSymbol(symbol);
                    rawData.setStockInfo(stockInfo);
                    rawData.setMainPageHtml(tuple.getT1());
                    rawData.setBalanceSheetHtml(tuple.getT2());
                    rawData.setIncomeStatementHtml(tuple.getT3());
                    rawData.setFinancialSummaryHtml(tuple.getT3()); // Reuse as needed
                    return rawData;
                });
    }

    @Override
    public Mono<String> fetchUrl(String url, boolean useProxy) {
        return fetchPage(url);
    }

    @Override
    public Mono<String> fetchUrlWithRetry(String url, boolean useProxy, int retries, long backoffMs) {
        return fetchPage(url)
                .retryWhen(Retry.backoff(retries, Duration.ofMillis(backoffMs)));
    }

    private Mono<String> fetchPage(String url) {
        return webClient.get()
                .uri(url)
                .header("User-Agent", Constants.DEFAULT_USER_AGENT)
                .header("Accept", Constants.DEFAULT_ACCEPT)
                .header("Accept-Language", Constants.DEFAULT_ACCEPT_LANGUAGE)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .onErrorResume(e -> {
                    log.error("Failed to fetch: {} - {}", url, e.getMessage());
                    return Mono.empty();
                });
    }
}