package com.tunindex.market_tool.domain.providers.stockanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockAnalysisProvider implements MarketDataProvider {

    private final DataParserService dataParserService;
    private final DataNormalizerService normalizer;
    private final DataEnricherService enricher;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() {
        return Constants.PROVIDER_STOCKANALYSIS;
    }

    @Override
    public Mono<EnrichedStockData> fetchStockData(String symbol) {
        log.info("📊 StockAnalysis Provider - Fetching data for symbol: {}", symbol);

        Constants.StockInfo stockInfo = Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS.get(symbol);
        if (stockInfo == null) {
            return Mono.error(new DataFetchException(
                    ErrorCodes.PROVIDER_NOT_FOUND,
                    getProviderName(),
                    symbol,
                    "Stock not found in configuration",
                    Collections.singletonList("Symbol: " + symbol)
            ));
        }

        String url = Constants.STOCKANALYSIS_BASE_URL + symbol + "/";

        return fetchPage(url)
                .flatMap(html -> extractStockDataFromPage(html, symbol, stockInfo))
                .filter(rawData -> rawData.getMainPageHtml() != null && !rawData.getMainPageHtml().isEmpty())
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ No data found for symbol: {}, skipping", symbol);
                    return Mono.empty();
                }))
                .map(dataParserService::parseToNormalized)
                .map(normalizer::toEntity)
                .flatMap(enricher::enrich)
                .doOnSuccess(data -> log.info("✨ Successfully enriched data for {} from StockAnalysis", symbol))
                .doOnError(error -> log.error("❌ Failed to fetch data for {}: {}", symbol, error.getMessage()));
    }

    @Override
    public Flux<EnrichedStockData> fetchAllStocks() {
        log.info("🚀 StockAnalysis Provider - Fetching all stocks");

        Map<String, Constants.StockInfo> stocks = Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS;

        return Flux.fromIterable(stocks.entrySet())
                .flatMap(entry -> fetchStockData(entry.getKey())
                        .onErrorResume(error -> {
                            log.error("❌ Failed to fetch {}: {}", entry.getKey(), error.getMessage());
                            return Mono.empty();
                        }), 5)
                .doOnComplete(() -> log.info("✅ Completed fetching all {} stocks from StockAnalysis", stocks.size()));
    }

    @Override
    public boolean supports(String symbol) {
        return Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS.containsKey(symbol);
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────

    private Mono<String> fetchPage(String url) {
        return webClient.get()
                .uri(url)
                .header("User-Agent", Constants.DEFAULT_USER_AGENT)
                .header("Accept", Constants.DEFAULT_ACCEPT)
                .header("Accept-Language", Constants.DEFAULT_ACCEPT_LANGUAGE)
                .header("Accept-Encoding", "identity") // disable gzip — get plain text
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .onErrorResume(e -> {
                    log.error("❌ Failed to fetch page: {} - {}", url, e.getMessage());
                    return Mono.empty();
                });
    }

    // ── Extraction ────────────────────────────────────────────────────────────

    private Mono<RawStockData> extractStockDataFromPage(String html, String symbol, Constants.StockInfo stockInfo) {
        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        Map<String, String> metrics = new LinkedHashMap<>();

        log.info("🔍 [{}}] HTML length: {}, contains 'marketCap': {}",
                symbol, html.length(), html.contains("marketCap"));

        // ── Quoted string fields  →  fieldName:"value" ────────────────────────
        extractQuoted(html, "marketCap",      metrics);
        extractQuoted(html, "revenue",        metrics);
        extractQuoted(html, "netIncome",      metrics);
        extractQuoted(html, "eps",            metrics);
        extractQuoted(html, "sharesOut",      metrics);
        extractQuoted(html, "peRatio",        metrics);
        extractQuoted(html, "forwardPE",      metrics);
        extractQuoted(html, "dividend",       metrics);
        extractQuoted(html, "averageVolume",  metrics);
        extractQuoted(html, "beta",           metrics);
        extractQuoted(html, "rsi",            metrics);
        extractQuoted(html, "earningsDate",   metrics);
        extractQuoted(html, "exDividendDate", metrics);
        extractQuoted(html, "exchange",       metrics);
        extractQuoted(html, "exchange_code",  metrics);
        extractQuoted(html, "dividendYield",  metrics);

        // ── Numeric fields  →  ,fieldName:123.45  or  {fieldName:123.45 ───────
        // Longer keys extracted first so the [,{] anchor prevents short-key
        // patterns from greedily matching inside longer key names.
        extractNumeric(html, "h52", metrics);
        extractNumeric(html, "l52", metrics);
        extractNumeric(html, "cp",  metrics);
        extractNumeric(html, "cl",  metrics);
        extractNumeric(html, "pd",  metrics);
        extractNumeric(html, "p",   metrics);
        extractNumeric(html, "v",   metrics);
        extractNumeric(html, "o",   metrics);
        extractNumeric(html, "h",   metrics);
        extractNumeric(html, "l",   metrics);
        extractNumeric(html, "c",   metrics);

        // ── Post-processing ───────────────────────────────────────────────────

        // Parse dividend yield out of "6.00 (4.14%)" when not already present
        if (metrics.containsKey("dividend") && !metrics.containsKey("dividendYield")) {
            Matcher ym = Pattern.compile("\\(([0-9.]+)%\\)").matcher(metrics.get("dividend"));
            if (ym.find()) {
                metrics.put("dividendYield", ym.group(1));
                log.info("📊 dividendYield (parsed from dividend): {}%", ym.group(1));
            }
        }

        // Strip yield suffix from dividend — keep only the number
        if (metrics.containsKey("dividend")) {
            Matcher nm = Pattern.compile("^([0-9.]+)").matcher(metrics.get("dividend"));
            if (nm.find()) metrics.put("dividend", nm.group(1));
        }

        // Build 52-week range string
        if (metrics.containsKey("l52") && metrics.containsKey("h52")) {
            metrics.put("week52Range", metrics.get("l52") + " - " + metrics.get("h52"));
        }

        // ── Guard ─────────────────────────────────────────────────────────────
        if (metrics.isEmpty()) {
            log.warn("⚠️ [{}}] No metrics extracted — page may not contain embedded data", symbol);
            return Mono.empty();
        }

        // ── Log summary ───────────────────────────────────────────────────────
        log.info("========================================");
        log.info("📊 EXTRACTED DATA FOR: {}", symbol);
        log.info("========================================");
        metrics.forEach((key, value) -> log.info("  {}: {}", key, value));
        log.info("========================================");
        log.info("✅ Successfully extracted {} metrics for {}", metrics.size(), symbol);

        // ── Build HTML for downstream parser ──────────────────────────────────
        String combinedHtml = buildHtmlWithMetrics(symbol, stockInfo, metrics);
        rawData.setMainPageHtml(combinedHtml);
        rawData.setBalanceSheetHtml(combinedHtml);
        rawData.setIncomeStatementHtml(combinedHtml);
        rawData.setFinancialSummaryHtml(combinedHtml);

        return Mono.just(rawData);
    }

    /**
     * Matches  fieldName:"some value"  in JS object literal HTML.
     * Skips empty strings and literal "n/a".
     */
    private void extractQuoted(String html, String fieldName, Map<String, String> metrics) {
        Pattern p = Pattern.compile(Pattern.quote(fieldName) + ":\"([^\"]+)\"");
        Matcher m = p.matcher(html);
        if (m.find()) {
            String value = m.group(1).trim();
            if (!value.isEmpty() && !value.equalsIgnoreCase("n/a")) {
                metrics.put(fieldName, value);
                log.info("📊 {}: {}", fieldName, value);
            }
        }
    }

    /**
     * Matches  ,fieldName:123.45  or  {fieldName:123.45  — the leading [,{]
     * ensures we never match a short key name inside a longer one (e.g. "h"
     * won't fire on "h52:10.3" because that starts with a comma, not a brace
     * followed by "h52" which is a different key).
     */
    private void extractNumeric(String html, String fieldName, Map<String, String> metrics) {
        Pattern p = Pattern.compile("[,{]" + Pattern.quote(fieldName) + ":(-?[0-9]+(?:\\.[0-9]+)?)");
        Matcher m = p.matcher(html);
        if (m.find()) {
            metrics.put(fieldName, m.group(1));
            log.info("📊 {}: {}", fieldName, m.group(1));
        }
    }

    // ── HTML builder ──────────────────────────────────────────────────────────

    private String buildHtmlWithMetrics(String symbol, Constants.StockInfo stockInfo,
                                        Map<String, String> metrics) {
        StringBuilder html = new StringBuilder();
        html.append("<div class='stock-analysis-data'>\n");
        html.append("  <div class='symbol'>").append(symbol).append("</div>\n");
        html.append("  <div class='company-name'>").append(stockInfo.name()).append("</div>\n");

        if (stockInfo.industry() != null && !stockInfo.industry().isEmpty())
            html.append("  <div class='industry'>Industry: ").append(stockInfo.industry()).append("</div>\n");
        if (stockInfo.country() != null && !stockInfo.country().isEmpty())
            html.append("  <div class='country'>Country: ").append(stockInfo.country()).append("</div>\n");

        // Overview
        html.append("  <div class='section overview'>\n    <h3>Overview</h3>\n");
        appendMetric(html, metrics, "marketCap",  "market-cap",         "Market Cap");
        appendMetric(html, metrics, "p",          "price",              "Price");
        appendMetric(html, metrics, "cp",         "change",             "Change %");
        appendMetric(html, metrics, "revenue",    "revenue",            "Revenue");
        appendMetric(html, metrics, "netIncome",  "net-income",         "Net Income");
        appendMetric(html, metrics, "eps",        "eps",                "EPS");
        appendMetric(html, metrics, "sharesOut",  "shares-outstanding", "Shares Outstanding");
        html.append("  </div>\n");

        // Valuation
        html.append("  <div class='section valuation'>\n    <h3>Valuation</h3>\n");
        appendMetric(html, metrics, "peRatio",   "pe-ratio",   "P/E Ratio");
        appendMetric(html, metrics, "forwardPE", "forward-pe", "Forward P/E");
        html.append("  </div>\n");

        // Dividends
        html.append("  <div class='section dividends'>\n    <h3>Dividends</h3>\n");
        appendMetric(html, metrics, "dividend",       "dividend",         "Dividend");
        appendMetric(html, metrics, "dividendYield",  "dividend-yield",   "Dividend Yield %");
        appendMetric(html, metrics, "exDividendDate", "ex-dividend-date", "Ex-Dividend Date");
        html.append("  </div>\n");

        // Price & Volume
        html.append("  <div class='section price-volume'>\n    <h3>Price & Volume</h3>\n");
        appendMetric(html, metrics, "v",             "volume",       "Volume");
        appendMetric(html, metrics, "averageVolume", "avg-volume",   "Average Volume");
        appendMetric(html, metrics, "o",             "open",         "Open");
        appendMetric(html, metrics, "cl",            "prev-close",   "Previous Close");
        appendMetric(html, metrics, "l",             "day-low",      "Day Low");
        appendMetric(html, metrics, "h",             "day-high",     "Day High");
        appendMetric(html, metrics, "l52",           "week52-low",   "52-Week Low");
        appendMetric(html, metrics, "h52",           "week52-high",  "52-Week High");
        appendMetric(html, metrics, "week52Range",   "week52-range", "52-Week Range");
        html.append("  </div>\n");

        // Technical
        html.append("  <div class='section technical'>\n    <h3>Technical</h3>\n");
        appendMetric(html, metrics, "beta", "beta", "Beta");
        appendMetric(html, metrics, "rsi",  "rsi",  "RSI");
        html.append("  </div>\n");

        // Exchange Info
        html.append("  <div class='section exchange-info'>\n    <h3>Exchange Info</h3>\n");
        appendMetric(html, metrics, "exchange",      "exchange",      "Exchange");
        appendMetric(html, metrics, "exchange_code", "exchange-code", "Exchange Code");
        appendMetric(html, metrics, "earningsDate",  "earnings-date", "Earnings Date");
        html.append("  </div>\n");

        html.append("</div>");
        return html.toString();
    }

    private void appendMetric(StringBuilder html, Map<String, String> metrics,
                              String key, String cssClass, String label) {
        if (metrics.containsKey(key)) {
            html.append("    <div class='").append(cssClass).append("'>")
                    .append(label).append(": ").append(metrics.get(key))
                    .append("</div>\n");
        }
    }
}