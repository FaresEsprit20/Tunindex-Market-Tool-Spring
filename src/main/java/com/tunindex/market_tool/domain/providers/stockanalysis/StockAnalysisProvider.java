package com.tunindex.market_tool.domain.providers.stockanalysis;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
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

        String overviewUrl = Constants.STOCKANALYSIS_BASE_URL + symbol + "/";
        String ratiosUrl = Constants.STOCKANALYSIS_BASE_URL + symbol + "/financials/ratios/";
        String statisticsUrl = Constants.STOCKANALYSIS_BASE_URL + symbol + "/statistics/";

        return Mono.zip(
                        fetchPage(overviewUrl),
                        fetchPage(ratiosUrl),
                        fetchPage(statisticsUrl)
                ).flatMap(tuple -> extractStockDataFromPages(tuple.getT1(), tuple.getT2(), tuple.getT3(), symbol, stockInfo))
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

    private Mono<String> fetchPage(String url) {
        return webClient.get()
                .uri(url)
                .header("User-Agent", Constants.DEFAULT_USER_AGENT)
                .header("Accept", Constants.DEFAULT_ACCEPT)
                .header("Accept-Language", Constants.DEFAULT_ACCEPT_LANGUAGE)
                .header("Accept-Encoding", "identity")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .onErrorResume(e -> {
                    log.error("❌ Failed to fetch page: {} - {}", url, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<RawStockData> extractStockDataFromPages(String overviewHtml, String ratiosHtml, String statisticsHtml,
                                                         String symbol, Constants.StockInfo stockInfo) {
        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        Map<String, String> metrics = new LinkedHashMap<>();

        log.info("🔍 Extracting data for {}", symbol);

        // Extract from overview page
        extractFromOverviewPage(overviewHtml, metrics);

        // Extract price data using multiple methods
        extractPriceDataFromOverview(overviewHtml, metrics);

        // Extract 52-week range from HTML tables as fallback
        extract52WeekFromHtml(overviewHtml, metrics);

        // Extract Debt/Equity from ratios page
        extractDebtEquityFromRatiosTable(ratiosHtml, metrics);

        // Extract Profit Margin and Book Value from statistics page
        extractProfitMarginFromStatisticsPage(statisticsHtml, metrics);
        extractBookValueFromStatisticsPage(statisticsHtml, metrics);

        // Post-processing
        if (metrics.containsKey("l52") && metrics.containsKey("h52")) {
            metrics.put("week52Range", metrics.get("l52") + " - " + metrics.get("h52"));
        }

        if (metrics.containsKey("ch1y")) {
            String oneYearReturn = metrics.get("ch1y").replace("+", "").replace("%", "");
            metrics.put("oneYearReturn", oneYearReturn);
        }

        if (metrics.isEmpty()) {
            log.warn("⚠️ No metrics extracted for {}", symbol);
            return Mono.empty();
        }

        log.info("========================================");
        log.info("📊 EXTRACTED DATA FOR: {}", symbol);
        log.info("========================================");
        metrics.forEach((key, value) -> log.info("  {}: {}", key, value));
        log.info("========================================");

        String combinedHtml = buildHtmlWithMetrics(symbol, stockInfo, metrics);
        rawData.setMainPageHtml(combinedHtml);
        rawData.setBalanceSheetHtml(combinedHtml);
        rawData.setIncomeStatementHtml(combinedHtml);
        rawData.setFinancialSummaryHtml(combinedHtml);

        return Mono.just(rawData);
    }

    private void extractFromOverviewPage(String html, Map<String, String> metrics) {
        extractQuoted(html, "marketCap", metrics);
        extractQuoted(html, "revenue", metrics);
        extractQuoted(html, "netIncome", metrics);
        extractQuoted(html, "sharesOut", metrics);
        extractQuoted(html, "eps", metrics);
        extractQuoted(html, "peRatio", metrics);
        extractQuoted(html, "forwardPE", metrics);
        extractQuoted(html, "averageVolume", metrics);
        extractQuoted(html, "beta", metrics);
        extractQuoted(html, "rsi", metrics);
        extractQuoted(html, "earningsDate", metrics);
        extractQuoted(html, "exDividendDate", metrics);
        extractQuoted(html, "exchange", metrics);
        extractQuoted(html, "exchange_code", metrics);
        extractQuoted(html, "dividendYield", metrics);
        extractQuoted(html, "ch1y", metrics);
        extractQuoted(html, "payoutRatio", metrics);
    }

    private void extractPriceDataFromOverview(String html, Map<String, String> metrics) {
        log.info("🔍 Extracting price data from overview page...");

        // Method 1: Extract from the quote object in the script
        // Look for "quote":{...} pattern
        Pattern quotePattern = Pattern.compile("\"quote\":\\s*\\{([^}]+)\\}");
        Matcher quoteMatcher = quotePattern.matcher(html);

        if (quoteMatcher.find()) {
            String quoteContent = quoteMatcher.group(1);
            log.info("Found quote object, extracting values...");

            extractJsonValue(quoteContent, "p", metrics);
            extractJsonValue(quoteContent, "c", metrics);
            extractJsonValue(quoteContent, "cp", metrics);
            extractJsonValue(quoteContent, "v", metrics);
            extractJsonValue(quoteContent, "o", metrics);
            extractJsonValue(quoteContent, "cl", metrics);
            extractJsonValue(quoteContent, "h", metrics);
            extractJsonValue(quoteContent, "l", metrics);
            extractJsonValue(quoteContent, "h52", metrics);
            extractJsonValue(quoteContent, "l52", metrics);
        }

        // Method 2: Direct search for patterns
        if (!metrics.containsKey("p")) {
            extractDirectValue(html, "\"p\":", metrics, "p");
        }
        if (!metrics.containsKey("cp")) {
            extractDirectValue(html, "\"cp\":", metrics, "cp");
        }
        if (!metrics.containsKey("v")) {
            extractDirectValue(html, "\"v\":", metrics, "v");
        }
        if (!metrics.containsKey("o")) {
            extractDirectValue(html, "\"o\":", metrics, "o");
        }
        if (!metrics.containsKey("cl")) {
            extractDirectValue(html, "\"cl\":", metrics, "cl");
        }
        if (!metrics.containsKey("h")) {
            extractDirectValue(html, "\"h\":", metrics, "h");
        }
        if (!metrics.containsKey("l")) {
            extractDirectValue(html, "\"l\":", metrics, "l");
        }
        if (!metrics.containsKey("h52")) {
            extractDirectValue(html, "\"h52\":", metrics, "h52");
        }
        if (!metrics.containsKey("l52")) {
            extractDirectValue(html, "\"l52\":", metrics, "l52");
        }
    }

    private void extractJsonValue(String json, String key, Map<String, String> metrics) {
        Pattern pattern = Pattern.compile("\"" + key + "\":([0-9.]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            metrics.put(key, matcher.group(1));
            log.info("📊 {}: {}", key, matcher.group(1));
        }
    }

    private void extractDirectValue(String html, String jsonKey, Map<String, String> metrics, String metricKey) {
        Pattern pattern = Pattern.compile(Pattern.quote(jsonKey) + "([0-9.]+)");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            metrics.put(metricKey, matcher.group(1));
            log.info("📊 {}: {}", metricKey, matcher.group(1));
        }
    }

    private void extract52WeekFromHtml(String html, Map<String, String> metrics) {
        if (metrics.containsKey("h52") && metrics.containsKey("l52")) {
            return;
        }

        log.info("🔍 Extracting 52-week range from HTML tables...");

        try {
            Document doc = Jsoup.parse(html);
            Elements tables = doc.select("table");

            for (Element table : tables) {
                Elements rows = table.select("tr");
                for (Element row : rows) {
                    String rowText = row.text();
                    if (rowText.contains("52-Week Range")) {
                        Elements cells = row.select("td");
                        if (cells.size() >= 2) {
                            String rangeText = cells.get(1).text().trim();
                            Pattern rangePattern = Pattern.compile("([0-9.]+)\\s*-\\s*([0-9.]+)");
                            Matcher matcher = rangePattern.matcher(rangeText);
                            if (matcher.find()) {
                                String low = matcher.group(1);
                                String high = matcher.group(2);
                                if (!metrics.containsKey("l52")) {
                                    metrics.put("l52", low);
                                    log.info("📊 l52: {}", low);
                                }
                                if (!metrics.containsKey("h52")) {
                                    metrics.put("h52", high);
                                    log.info("📊 h52: {}", high);
                                }
                                metrics.put("week52Range", rangeText);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse HTML for 52-week range: {}", e.getMessage());
        }
    }

    private void extractDebtEquityFromRatiosTable(String html, Map<String, String> metrics) {
        log.info("🔍 Looking for Debt/Equity in ratios table...");

        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("tr");

        for (Element row : rows) {
            if (row.text().contains("Debt / Equity Ratio")) {
                Elements dataCells = row.select("td");
                if (dataCells.size() >= 2) {
                    String value = dataCells.get(1).text().trim();
                    if (!value.isEmpty() && !value.equals("n/a") && !value.equals("Debt / Equity Ratio")) {
                        metrics.put("debtToEquity", value);
                        log.info("📊 debtToEquity: {}", value);
                        return;
                    }
                }
                break;
            }
        }

        Pattern pattern = Pattern.compile("\"debtequity\":\\[([0-9.\\-,\\s]+)\\]");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String arrayStr = matcher.group(1);
            String[] values = arrayStr.split(",");
            if (values.length > 0) {
                String value = values[0].trim();
                metrics.put("debtToEquity", value);
                log.info("📊 debtToEquity (from JSON): {}", value);
            }
        }
    }

    private void extractProfitMarginFromStatisticsPage(String html, Map<String, String> metrics) {
        log.info("🔍 Looking for Profit Margin in statistics page...");

        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("tr");

        for (Element row : rows) {
            if (row.text().contains("Profit Margin")) {
                Elements dataCells = row.select("td");
                if (dataCells.size() >= 2) {
                    String value = dataCells.get(1).text().trim();
                    if (!value.isEmpty() && !value.equals("n/a")) {
                        value = value.replace("%", "");
                        metrics.put("profitMargin", value);
                        log.info("📊 profitMargin: {}%", value);
                        return;
                    }
                }
                break;
            }
        }

        Pattern pattern = Pattern.compile("\"profitMargin\":\"([0-9.]+)%\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String value = matcher.group(1);
            metrics.put("profitMargin", value);
            log.info("📊 profitMargin (from JSON): {}%", value);
        }
    }

    private void extractBookValueFromStatisticsPage(String html, Map<String, String> metrics) {
        log.info("🔍 Looking for Book Value Per Share in statistics page...");

        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("tr");

        for (Element row : rows) {
            if (row.text().contains("Book Value Per Share")) {
                Elements dataCells = row.select("td");
                if (dataCells.size() >= 2) {
                    String value = dataCells.get(1).text().trim();
                    if (!value.isEmpty() && !value.equals("n/a")) {
                        metrics.put("bookValuePerShare", value);
                        log.info("📊 bookValuePerShare: {}", value);
                        return;
                    }
                }
                break;
            }
        }

        Pattern pattern = Pattern.compile("\"bvps\":\"([0-9.]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String value = matcher.group(1);
            metrics.put("bookValuePerShare", value);
            log.info("📊 bookValuePerShare (from JSON): {}", value);
        }
    }

    private void extractQuoted(String html, String fieldName, Map<String, String> metrics) {
        Pattern p = Pattern.compile(Pattern.quote(fieldName) + ":\"([^\"]+)\"");
        Matcher m = p.matcher(html);
        if (m.find()) {
            String value = m.group(1).trim();
            if (!value.isEmpty() && !value.equalsIgnoreCase("n/a") && !value.equalsIgnoreCase("null")) {
                metrics.put(fieldName, value);
                log.info("📊 {}: {}", fieldName, value);
            }
        }
    }

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

        html.append("  <div class='section overview'>\n    <h3>Overview</h3>\n");
        appendMetric(html, metrics, "marketCap", "market-cap", "Market Cap");
        appendMetric(html, metrics, "p", "price", "Price");
        appendMetric(html, metrics, "cp", "change", "Change %");
        appendMetric(html, metrics, "revenue", "revenue", "Revenue");
        appendMetric(html, metrics, "netIncome", "net-income", "Net Income");
        appendMetric(html, metrics, "eps", "eps", "EPS");
        appendMetric(html, metrics, "sharesOut", "shares-outstanding", "Shares Outstanding");
        html.append("  </div>\n");

        html.append("  <div class='section valuation'>\n    <h3>Valuation</h3>\n");
        appendMetric(html, metrics, "peRatio", "pe-ratio", "P/E Ratio");
        appendMetric(html, metrics, "forwardPE", "forward-pe", "Forward P/E");
        html.append("  </div>\n");

        html.append("  <div class='section financial-health'>\n    <h3>Financial Health</h3>\n");
        appendMetric(html, metrics, "debtToEquity", "debt-equity", "Debt/Equity");
        appendMetric(html, metrics, "profitMargin", "profit-margin", "Profit Margin %");
        html.append("  </div>\n");

        html.append("  <div class='section dividends'>\n    <h3>Dividends</h3>\n");
        appendMetric(html, metrics, "dividendYield", "dividend-yield", "Dividend Yield %");
        appendMetric(html, metrics, "payoutRatio", "payout-ratio", "Payout Ratio %");
        appendMetric(html, metrics, "exDividendDate", "ex-dividend-date", "Ex-Dividend Date");
        html.append("  </div>\n");

        html.append("  <div class='section book-value'>\n    <h3>Book Value</h3>\n");
        appendMetric(html, metrics, "bookValuePerShare", "book-value-per-share", "Book Value Per Share");
        html.append("  </div>\n");

        html.append("  <div class='section price-volume'>\n    <h3>Price & Volume</h3>\n");
        appendMetric(html, metrics, "v", "volume", "Volume");
        appendMetric(html, metrics, "averageVolume", "avg-volume", "Average Volume");
        appendMetric(html, metrics, "o", "open", "Open");
        appendMetric(html, metrics, "cl", "prev-close", "Previous Close");
        appendMetric(html, metrics, "l", "day-low", "Day Low");
        appendMetric(html, metrics, "h", "day-high", "Day High");
        appendMetric(html, metrics, "l52", "week52-low", "52-Week Low");
        appendMetric(html, metrics, "h52", "week52-high", "52-Week High");
        appendMetric(html, metrics, "week52Range", "week52-range", "52-Week Range");
        html.append("  </div>\n");

        html.append("  <div class='section technical'>\n    <h3>Technical</h3>\n");
        appendMetric(html, metrics, "beta", "beta", "Beta");
        appendMetric(html, metrics, "rsi", "rsi", "RSI");
        html.append("  </div>\n");

        html.append("  <div class='section performance'>\n    <h3>Performance</h3>\n");
        appendMetric(html, metrics, "oneYearReturn", "one-year-return", "1 Year Return %");
        html.append("  </div>\n");

        html.append("  <div class='section exchange-info'>\n    <h3>Exchange Info</h3>\n");
        appendMetric(html, metrics, "exchange", "exchange", "Exchange");
        appendMetric(html, metrics, "exchange_code", "exchange-code", "Exchange Code");
        appendMetric(html, metrics, "earningsDate", "earnings-date", "Earnings Date");
        html.append("  </div>\n");

        html.append("</div>");
        return html.toString();
    }

    private void appendMetric(StringBuilder html, Map<String, String> metrics,
                              String key, String cssClass, String label) {
        if (metrics.containsKey(key) && metrics.get(key) != null && !metrics.get(key).isEmpty()) {
            String value = metrics.get(key);
            if (value.endsWith("%")) {
                value = value.replace("%", "");
            }
            html.append("    <div class='").append(cssClass).append("'>")
                    .append(label).append(": ").append(value)
                    .append("</div>\n");
        }
    }
}