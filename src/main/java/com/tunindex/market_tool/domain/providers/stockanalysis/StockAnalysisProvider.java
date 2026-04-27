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
                    log.error("❌ Failed to fetch page: {} - {}", url, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<RawStockData> extractStockDataFromPage(String html, String symbol, Constants.StockInfo stockInfo) {
        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        Map<String, String> metrics = new LinkedHashMap<>();

        log.info("🔍 Extracting data for {} from page source", symbol);

        try {
            // Search the full HTML directly - the data is already there

            // String fields (quoted values)
            extractField(html, "marketCap", metrics);
            extractField(html, "revenue", metrics);
            extractField(html, "netIncome", metrics);
            extractField(html, "eps", metrics);
            extractField(html, "sharesOut", metrics);
            extractField(html, "peRatio", metrics);
            extractField(html, "forwardPE", metrics);
            extractField(html, "dividend", metrics);
            extractField(html, "averageVolume", metrics);
            extractField(html, "beta", metrics);
            extractField(html, "rsi", metrics);
            extractField(html, "earningsDate", metrics);
            extractField(html, "exDividendDate", metrics);
            extractField(html, "exchange", metrics);
            extractField(html, "exchange_code", metrics);

            // Numeric fields from quote object (no quotes)
            extractNumericField(html, "h52", metrics);  // 52-week high
            extractNumericField(html, "l52", metrics);  // 52-week low
            extractNumericField(html, "p", metrics);    // current price
            extractNumericField(html, "cp", metrics);   // change percentage
            extractNumericField(html, "v", metrics);    // volume
            extractNumericField(html, "cl", metrics);   // previous close
            extractNumericField(html, "o", metrics);    // open
            extractNumericField(html, "h", metrics);    // day high
            extractNumericField(html, "l", metrics);    // day low

            // Extract dividend yield from dividend string
            if (metrics.containsKey("dividend")) {
                String dividendValue = metrics.get("dividend");
                Pattern yieldPattern = Pattern.compile("\\(([0-9.]+)%\\)");
                Matcher yieldMatcher = yieldPattern.matcher(dividendValue);
                if (yieldMatcher.find()) {
                    metrics.put("dividendYield", yieldMatcher.group(1));
                    log.info("💸 Dividend Yield: {}%", yieldMatcher.group(1));
                }
                // Extract just the number part
                Pattern numPattern = Pattern.compile("^([0-9.]+)");
                Matcher numMatcher = numPattern.matcher(dividendValue);
                if (numMatcher.find()) {
                    metrics.put("dividend", numMatcher.group(1));
                }
            }

            // Calculate week52 range string
            if (metrics.containsKey("l52") && metrics.containsKey("h52")) {
                metrics.put("week52Range", metrics.get("l52") + " - " + metrics.get("h52"));
            }

            // Check if we have any data
            if (metrics.isEmpty()) {
                log.warn("⚠️ No metrics extracted for {}", symbol);
                return Mono.empty();
            }

        } catch (Exception e) {
            log.error("Error extracting data for {}: {}", symbol, e.getMessage());
            return Mono.empty();
        }

        // Log summary
        log.info("========================================");
        log.info("📊 EXTRACTED DATA FOR: {}", symbol);
        log.info("========================================");
        metrics.forEach((key, value) -> log.info("  {}: {}", key, value));
        log.info("========================================");
        log.info("✅ Successfully extracted {} metrics for {}", metrics.size(), symbol);

        // Build HTML for parser
        String combinedHtml = buildHtmlWithMetrics(symbol, stockInfo, metrics);

        rawData.setMainPageHtml(combinedHtml);
        rawData.setBalanceSheetHtml(combinedHtml);
        rawData.setIncomeStatementHtml(combinedHtml);
        rawData.setFinancialSummaryHtml(combinedHtml);

        return Mono.just(rawData);
    }

    private void extractField(String html, String fieldName, Map<String, String> metrics) {
        Pattern pattern = Pattern.compile(fieldName + ":\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isEmpty() && !value.equals("n/a")) {
                metrics.put(fieldName, value);
                log.info("📊 {}: {}", getDisplayName(fieldName), value);
            }
        }
    }

    private void extractNumericField(String html, String fieldName, Map<String, String> metrics) {
        Pattern pattern = Pattern.compile("\\b" + fieldName + ":\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isEmpty()) {
                metrics.put(fieldName, value);
                log.info("📊 {}: {}", getDisplayName(fieldName), value);
            }
        }
    }

    private String getDisplayName(String fieldName) {
        switch (fieldName) {
            case "marketCap": return "🏦 Market Cap";
            case "revenue": return "💰 Revenue";
            case "netIncome": return "📊 Net Income";
            case "eps": return "💵 EPS";
            case "sharesOut": return "📋 Shares Outstanding";
            case "peRatio": return "📐 P/E Ratio";
            case "forwardPE": return "🔮 Forward P/E";
            case "dividend": return "💸 Dividend";
            case "dividendYield": return "💸 Dividend Yield";
            case "averageVolume": return "📈 Average Volume";
            case "beta": return "📊 Beta";
            case "rsi": return "📈 RSI";
            case "earningsDate": return "📅 Earnings Date";
            case "exDividendDate": return "📅 Ex-Dividend Date";
            case "exchange": return "🏛️ Exchange";
            case "exchange_code": return "🏛️ Exchange Code";
            case "h52": return "📈 52-Week High";
            case "l52": return "📉 52-Week Low";
            case "p": return "💰 Price";
            case "cp": return "📉 Change %";
            case "v": return "📊 Volume";
            case "cl": return "🔚 Previous Close";
            case "o": return "🎯 Open";
            case "h": return "📈 Day High";
            case "l": return "📉 Day Low";
            case "week52Range": return "📆 52-Week Range";
            default: return fieldName;
        }
    }

    private String buildHtmlWithMetrics(String symbol, Constants.StockInfo stockInfo, Map<String, String> metrics) {
        StringBuilder html = new StringBuilder();
        html.append("<div class='stock-analysis-data'>\n");
        html.append("  <div class='symbol'>").append(symbol).append("</div>\n");
        html.append("  <div class='company-name'>").append(stockInfo.name()).append("</div>\n");

        if (stockInfo.industry() != null && !stockInfo.industry().isEmpty()) {
            html.append("  <div class='industry'>Industry: ").append(stockInfo.industry()).append("</div>\n");
        }
        if (stockInfo.country() != null && !stockInfo.country().isEmpty()) {
            html.append("  <div class='country'>Country: ").append(stockInfo.country()).append("</div>\n");
        }

        // Overview Section
        html.append("  <div class='section overview'>\n");
        html.append("    <h3>Overview</h3>\n");
        if (metrics.containsKey("marketCap")) html.append("    <div class='market-cap'>Market Cap: ").append(metrics.get("marketCap")).append("</div>\n");
        if (metrics.containsKey("p")) html.append("    <div class='price'>Price: ").append(metrics.get("p")).append("</div>\n");
        if (metrics.containsKey("cp")) html.append("    <div class='change'>Change: ").append(metrics.get("cp")).append("%</div>\n");
        if (metrics.containsKey("revenue")) html.append("    <div class='revenue'>Revenue: ").append(metrics.get("revenue")).append("</div>\n");
        if (metrics.containsKey("netIncome")) html.append("    <div class='net-income'>Net Income: ").append(metrics.get("netIncome")).append("</div>\n");
        if (metrics.containsKey("eps")) html.append("    <div class='eps'>EPS: ").append(metrics.get("eps")).append("</div>\n");
        if (metrics.containsKey("sharesOut")) html.append("    <div class='shares-outstanding'>Shares Outstanding: ").append(metrics.get("sharesOut")).append("</div>\n");
        html.append("  </div>\n");

        // Valuation Section
        html.append("  <div class='section valuation'>\n");
        html.append("    <h3>Valuation</h3>\n");
        if (metrics.containsKey("peRatio")) html.append("    <div class='pe-ratio'>P/E Ratio: ").append(metrics.get("peRatio")).append("</div>\n");
        if (metrics.containsKey("forwardPE")) html.append("    <div class='forward-pe'>Forward P/E: ").append(metrics.get("forwardPE")).append("</div>\n");
        html.append("  </div>\n");

        // Dividends Section
        html.append("  <div class='section dividends'>\n");
        html.append("    <h3>Dividends</h3>\n");
        if (metrics.containsKey("dividend")) html.append("    <div class='dividend'>Dividend: ").append(metrics.get("dividend")).append("</div>\n");
        if (metrics.containsKey("dividendYield")) html.append("    <div class='dividend-yield'>Dividend Yield: ").append(metrics.get("dividendYield")).append("%</div>\n");
        if (metrics.containsKey("exDividendDate")) html.append("    <div class='ex-dividend-date'>Ex-Dividend Date: ").append(metrics.get("exDividendDate")).append("</div>\n");
        html.append("  </div>\n");

        // Price & Volume Section
        html.append("  <div class='section price-volume'>\n");
        html.append("    <h3>Price & Volume</h3>\n");
        if (metrics.containsKey("v")) html.append("    <div class='volume'>Volume: ").append(metrics.get("v")).append("</div>\n");
        if (metrics.containsKey("averageVolume")) html.append("    <div class='avg-volume'>Average Volume: ").append(metrics.get("averageVolume")).append("</div>\n");
        if (metrics.containsKey("o")) html.append("    <div class='open'>Open: ").append(metrics.get("o")).append("</div>\n");
        if (metrics.containsKey("cl")) html.append("    <div class='prev-close'>Previous Close: ").append(metrics.get("cl")).append("</div>\n");
        if (metrics.containsKey("l")) html.append("    <div class='day-low'>Day Low: ").append(metrics.get("l")).append("</div>\n");
        if (metrics.containsKey("h")) html.append("    <div class='day-high'>Day High: ").append(metrics.get("h")).append("</div>\n");
        if (metrics.containsKey("l52")) html.append("    <div class='week52-low'>52-Week Low: ").append(metrics.get("l52")).append("</div>\n");
        if (metrics.containsKey("h52")) html.append("    <div class='week52-high'>52-Week High: ").append(metrics.get("h52")).append("</div>\n");
        if (metrics.containsKey("week52Range")) html.append("    <div class='week52-range'>52-Week Range: ").append(metrics.get("week52Range")).append("</div>\n");
        html.append("  </div>\n");

        // Technical Section
        html.append("  <div class='section technical'>\n");
        html.append("    <h3>Technical</h3>\n");
        if (metrics.containsKey("beta")) html.append("    <div class='beta'>Beta: ").append(metrics.get("beta")).append("</div>\n");
        if (metrics.containsKey("rsi")) html.append("    <div class='rsi'>RSI: ").append(metrics.get("rsi")).append("</div>\n");
        html.append("  </div>\n");

        // Exchange Info Section
        html.append("  <div class='section exchange-info'>\n");
        html.append("    <h3>Exchange Info</h3>\n");
        if (metrics.containsKey("exchange")) html.append("    <div class='exchange'>Exchange: ").append(metrics.get("exchange")).append("</div>\n");
        if (metrics.containsKey("exchange_code")) html.append("    <div class='exchange-code'>Exchange Code: ").append(metrics.get("exchange_code")).append("</div>\n");
        if (metrics.containsKey("earningsDate")) html.append("    <div class='earnings-date'>Earnings Date: ").append(metrics.get("earningsDate")).append("</div>\n");
        html.append("  </div>\n");

        html.append("</div>");

        return html.toString();
    }
}