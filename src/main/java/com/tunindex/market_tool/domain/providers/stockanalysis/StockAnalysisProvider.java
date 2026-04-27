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
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
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
        log.info("Fetching data for symbol: {}", symbol);

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
                )
                .map(tuple -> {
                    String overviewHtml = tuple.getT1();
                    String ratiosHtml = tuple.getT2();
                    String statisticsHtml = tuple.getT3();

                    Map<String, Object> overviewData = extractAllStockDataFromJson(overviewHtml, symbol);
                    Map<String, Object> ratiosData = extractRatiosDataFromJson(ratiosHtml, symbol);
                    Map<String, Object> statisticsData = extractStatisticsDataFromJson(statisticsHtml, symbol);

                    Map<String, Object> mergedData = new HashMap<>();
                    mergedData.putAll(overviewData);
                    mergedData.putAll(ratiosData);
                    mergedData.putAll(statisticsData);

                    return buildRawStockData(symbol, stockInfo, mergedData);
                })
                .map(dataParserService::parseToNormalized)
                .map(normalizer::toEntity)
                .flatMap(enricher::enrich)
                .doOnSuccess(data -> log.info("Successfully fetched and enriched data for {} from StockAnalysis", symbol))
                .doOnError(error -> log.error("Failed to fetch data for {}: {}", symbol, error.getMessage()));
    }

    @Override
    public Flux<EnrichedStockData> fetchAllStocks() {
        log.info("Fetching all stocks from StockAnalysis.com");

        Map<String, Constants.StockInfo> stocks = Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS;

        return Flux.fromIterable(stocks.entrySet())
                .flatMap(entry -> fetchStockData(entry.getKey())
                        .onErrorResume(error -> {
                            log.error("Failed to fetch {}: {}", entry.getKey(), error.getMessage());
                            return Mono.empty();
                        }), 3)
                .doOnComplete(() -> log.info("Completed fetching all stocks from StockAnalysis"));
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
                .header("Accept-Encoding", Constants.DEFAULT_ACCEPT_ENCODING)
                .header("Connection", Constants.DEFAULT_CONNECTION)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .onErrorResume(e -> {
                    log.error("Failed to fetch page: {} - {}", url, e.getMessage());
                    return Mono.empty();
                });
    }

    private RawStockData buildRawStockData(String symbol, Constants.StockInfo stockInfo, Map<String, Object> data) {
        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        String combinedHtml = buildComprehensiveHtml(symbol, stockInfo, data);

        rawData.setMainPageHtml(combinedHtml);
        rawData.setBalanceSheetHtml(combinedHtml);
        rawData.setIncomeStatementHtml(combinedHtml);
        rawData.setFinancialSummaryHtml(combinedHtml);

        log.info("Successfully extracted data for {}: {}", symbol, data.keySet());

        return rawData;
    }

    private Map<String, Object> extractAllStockDataFromJson(String html, String targetSymbol) {
        Map<String, Object> data = new HashMap<>();

        try {
            Pattern pattern = Pattern.compile("stockData:\\s*(\\[\\{.*?\\}\\])", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String stockDataJson = matcher.group(1);
                JsonNode stockArray = objectMapper.readTree(stockDataJson);

                for (JsonNode stock : stockArray) {
                    String symbol = stock.has("s") ? stock.get("s").asText().replace("bvmt/", "") : "";

                    if (symbol.equals(targetSymbol)) {
                        Iterator<Map.Entry<String, JsonNode>> fields = stock.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            String fieldName = field.getKey();
                            JsonNode fieldValue = field.getValue();

                            if (fieldValue != null && !fieldValue.isNull()) {
                                if (fieldValue.isNumber()) {
                                    data.put(fieldName, fieldValue.asDouble());
                                } else if (fieldValue.isTextual()) {
                                    String textValue = fieldValue.asText();
                                    if (!textValue.isEmpty() && !textValue.equals("-")) {
                                        data.put(fieldName, textValue);
                                    }
                                } else if (fieldValue.isBoolean()) {
                                    data.put(fieldName, fieldValue.asBoolean());
                                } else if (fieldValue.isInt()) {
                                    data.put(fieldName, fieldValue.asInt());
                                }
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract JSON data from overview for {}: {}", targetSymbol, e.getMessage());
        }

        return data;
    }

    private Map<String, Object> extractRatiosDataFromJson(String html, String targetSymbol) {
        Map<String, Object> data = new HashMap<>();

        try {
            Pattern pattern = Pattern.compile("financialData:\\s*(\\{[^}]*\\})", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String financialDataJson = matcher.group(1);
                JsonNode financialData = objectMapper.readTree(financialDataJson);

                JsonNode dateKeyNode = financialData.get("datekey");
                if (dateKeyNode != null && dateKeyNode.isArray() && dateKeyNode.size() > 0) {
                    int latestColumnIndex = 0;
                    String firstColumn = dateKeyNode.get(0).asText();
                    if (!"Current".equals(firstColumn) && !"TTM".equals(firstColumn) && dateKeyNode.size() > 1) {
                        latestColumnIndex = 0;
                    }

                    extractRatioMetric(financialData, "pe", latestColumnIndex, data);
                    extractRatioMetric(financialData, "pb", latestColumnIndex, data);
                    extractRatioMetric(financialData, "dividendyield", latestColumnIndex, data);
                    extractRatioMetric(financialData, "debtequity", latestColumnIndex, data);
                    extractRatioMetric(financialData, "ps", latestColumnIndex, data);
                    extractRatioMetric(financialData, "payoutratio", latestColumnIndex, data);
                    extractRatioMetric(financialData, "roe", latestColumnIndex, data);
                    extractRatioMetric(financialData, "roa", latestColumnIndex, data);
                    extractRatioMetric(financialData, "marketcap", latestColumnIndex, data);
                    extractRatioMetric(financialData, "lastCloseRatios", latestColumnIndex, data);
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract ratios data for {}: {}", targetSymbol, e.getMessage());
        }

        return data;
    }

    private Map<String, Object> extractStatisticsDataFromJson(String html, String targetSymbol) {
        Map<String, Object> data = new HashMap<>();

        try {
            Pattern profitMarginPattern = Pattern.compile("\"profitMargin\":\\s*\\{[^}]*\"value\":\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher profitMarginMatcher = profitMarginPattern.matcher(html);
            if (profitMarginMatcher.find()) {
                String profitMarginStr = profitMarginMatcher.group(1);
                profitMarginStr = profitMarginStr.replace("%", "").trim();
                try {
                    data.put("profitMargin", Double.parseDouble(profitMarginStr));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse profitMargin: {}", profitMarginStr);
                }
            }

            Pattern bvpsPattern = Pattern.compile("\"bvps\":\\s*\\{[^}]*\"value\":\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher bvpsMatcher = bvpsPattern.matcher(html);
            if (bvpsMatcher.find()) {
                String bvpsStr = bvpsMatcher.group(1);
                try {
                    data.put("bookValuePerShare", Double.parseDouble(bvpsStr));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse bookValuePerShare: {}", bvpsStr);
                }
            }

            Pattern epsPattern = Pattern.compile("\"eps\":\\s*\\{[^}]*\"value\":\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher epsMatcher = epsPattern.matcher(html);
            if (epsMatcher.find()) {
                String epsStr = epsMatcher.group(1);
                try {
                    data.put("eps", Double.parseDouble(epsStr));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse EPS: {}", epsStr);
                }
            }

            Pattern betaPattern = Pattern.compile("\"beta\":\\s*\\{[^}]*\"value\":\\s*\"([^\"]+)\"", Pattern.DOTALL);
            Matcher betaMatcher = betaPattern.matcher(html);
            if (betaMatcher.find()) {
                try {
                    data.put("beta", Double.parseDouble(betaMatcher.group(1)));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse beta");
                }
            }

            // Extract 52-week price change
            Pattern week52ChangePattern = Pattern.compile("\"ch1y\":\\s*([0-9.]+)", Pattern.DOTALL);
            Matcher week52Matcher = week52ChangePattern.matcher(html);
            if (week52Matcher.find()) {
                try {
                    data.put("oneYearReturn", Double.parseDouble(week52Matcher.group(1)));
                } catch (NumberFormatException e) {
                    log.debug("Failed to parse 52-week change: {}", week52Matcher.group(1));
                }
            }

        } catch (Exception e) {
            log.error("Failed to extract statistics data for {}: {}", targetSymbol, e.getMessage());
        }

        return data;
    }

    private void extractRatioMetric(JsonNode financialData, String metricName, int columnIndex, Map<String, Object> data) {
        try {
            JsonNode metricNode = financialData.get(metricName);
            if (metricNode != null && metricNode.isArray() && metricNode.size() > columnIndex) {
                JsonNode valueNode = metricNode.get(columnIndex);
                if (valueNode != null && !valueNode.isNull()) {
                    if (valueNode.isNumber()) {
                        data.put(metricName, valueNode.asDouble());
                    } else if (valueNode.isTextual()) {
                        String textValue = valueNode.asText();
                        if (!textValue.isEmpty() && !"-".equals(textValue)) {
                            try {
                                data.put(metricName, Double.parseDouble(textValue));
                            } catch (NumberFormatException e) {
                                data.put(metricName, textValue);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract metric {}: {}", metricName, e.getMessage());
        }
    }

    private String buildComprehensiveHtml(String symbol, Constants.StockInfo stockInfo, Map<String, Object> data) {
        BigDecimal currentPrice = data.containsKey("price") ? BigDecimal.valueOf((Double) data.get("price")) : null;
        BigDecimal week52High = data.containsKey("week52High") ? BigDecimal.valueOf((Double) data.get("week52High")) : null;
        BigDecimal week52Low = data.containsKey("week52Low") ? BigDecimal.valueOf((Double) data.get("week52Low")) : null;

        // Calculate 52-week percentages
        BigDecimal highPercentage = null;
        BigDecimal lowPercentage = null;
        BigDecimal distanceFromHigh = null;
        BigDecimal distanceFromLow = null;

        if (currentPrice != null && week52High != null && week52High.compareTo(BigDecimal.ZERO) > 0) {
            highPercentage = currentPrice.divide(week52High, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            distanceFromHigh = week52High.subtract(currentPrice);
        }

        if (currentPrice != null && week52Low != null && week52Low.compareTo(BigDecimal.ZERO) > 0) {
            lowPercentage = currentPrice.divide(week52Low, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            distanceFromLow = currentPrice.subtract(week52Low);
        }

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
        if (stockInfo.ownershipType() != null) {
            html.append("  <div class='ownership-type'>Ownership: ").append(stockInfo.ownershipType()).append("</div>\n");
        }

        // ============ OVERVIEW SECTION ============
        html.append("  <div class='section overview'>\n");
        html.append("    <h3>Overview</h3>\n");
        if (data.containsKey("marketCap")) html.append("    <div class='market-cap'>Market Cap: ").append(formatNumber((Double) data.get("marketCap"))).append("</div>\n");
        if (data.containsKey("price")) html.append("    <div class='price'>Price: ").append(data.get("price")).append("</div>\n");
        if (data.containsKey("change")) html.append("    <div class='change'>Change: ").append(data.get("change")).append("%</div>\n");
        if (data.containsKey("revenue")) html.append("    <div class='revenue'>Revenue: ").append(formatNumber((Double) data.get("revenue"))).append("</div>\n");
        if (data.containsKey("eps")) html.append("    <div class='eps'>EPS: ").append(data.get("eps")).append("</div>\n");
        if (data.containsKey("oneYearReturn")) html.append("    <div class='one-year-return'>52-Week Return: ").append(data.get("oneYearReturn")).append("%</div>\n");
        html.append("  </div>\n");

        // ============ 52-WEEK PRICE SECTION ============
        html.append("  <div class='section week52'>\n");
        html.append("    <h3>52-Week Price Range</h3>\n");

        if (week52High != null) {
            html.append("    <div class='week52-high'>52-Week High: ").append(week52High).append("</div>\n");
            if (highPercentage != null) {
                html.append("    <div class='week52-high-percentage'>Current vs High: ").append(String.format("%.2f", highPercentage)).append("%</div>\n");
            }
            if (distanceFromHigh != null) {
                html.append("    <div class='distance-from-high'>Distance from High: ").append(String.format("%.2f", distanceFromHigh)).append("</div>\n");
            }
        }

        if (week52Low != null) {
            html.append("    <div class='week52-low'>52-Week Low: ").append(week52Low).append("</div>\n");
            if (lowPercentage != null) {
                html.append("    <div class='week52-low-percentage'>Current vs Low: ").append(String.format("%.2f", lowPercentage)).append("%</div>\n");
            }
            if (distanceFromLow != null) {
                html.append("    <div class='distance-from-low'>Distance from Low: ").append(String.format("%.2f", distanceFromLow)).append("</div>\n");
            }
        }

        if (week52Low != null && week52High != null) {
            BigDecimal range = week52High.subtract(week52Low);
            html.append("    <div class='week52-range'>52-Week Range: ").append(week52Low).append(" - ").append(week52High).append("</div>\n");
            html.append("    <div class='week52-range-width'>Range Width: ").append(String.format("%.2f", range)).append("</div>\n");

            if (currentPrice != null && range.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal positionInRange = currentPrice.subtract(week52Low)
                        .divide(range, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                html.append("    <div class='position-in-range'>Position in Range: ").append(String.format("%.2f", positionInRange)).append("%</div>\n");
            }
        }
        html.append("  </div>\n");

        // ============ VALUATION RATIOS SECTION ============
        html.append("  <div class='section valuation-ratios'>\n");
        html.append("    <h3>Valuation Ratios</h3>\n");
        if (data.containsKey("pe")) html.append("    <div class='pe-ratio'>P/E Ratio: ").append(String.format("%.2f", (Double) data.get("pe"))).append("</div>\n");
        if (data.containsKey("pb")) html.append("    <div class='pb-ratio'>P/B Ratio: ").append(String.format("%.2f", (Double) data.get("pb"))).append("</div>\n");
        if (data.containsKey("ps")) html.append("    <div class='ps-ratio'>P/S Ratio: ").append(String.format("%.2f", (Double) data.get("ps"))).append("</div>\n");
        html.append("  </div>\n");

        // ============ FINANCIAL HEALTH SECTION ============
        html.append("  <div class='section financial-health'>\n");
        html.append("    <h3>Financial Health</h3>\n");
        if (data.containsKey("debtequity")) html.append("    <div class='debt-equity'>Debt/Equity: ").append(String.format("%.2f", (Double) data.get("debtequity"))).append("</div>\n");
        if (data.containsKey("roe")) html.append("    <div class='roe'>ROE: ").append(String.format("%.2f%%", (Double) data.get("roe") * 100)).append("</div>\n");
        if (data.containsKey("roa")) html.append("    <div class='roa'>ROA: ").append(String.format("%.2f%%", (Double) data.get("roa") * 100)).append("</div>\n");
        if (data.containsKey("bookValuePerShare")) html.append("    <div class='book-value-per-share'>Book Value Per Share: ").append(String.format("%.2f", (Double) data.get("bookValuePerShare"))).append("</div>\n");
        if (data.containsKey("beta")) html.append("    <div class='beta'>Beta: ").append(String.format("%.2f", (Double) data.get("beta"))).append("</div>\n");
        html.append("  </div>\n");

        // ============ PROFITABILITY & MARGINS SECTION ============
        html.append("  <div class='section profitability'>\n");
        html.append("    <h3>Profitability & Margins</h3>\n");
        if (data.containsKey("profitMargin")) html.append("    <div class='profit-margin'>Profit Margin: ").append(String.format("%.2f%%", (Double) data.get("profitMargin"))).append("</div>\n");
        html.append("  </div>\n");

        // ============ DIVIDENDS SECTION ============
        html.append("  <div class='section dividends'>\n");
        html.append("    <h3>Dividends</h3>\n");
        if (data.containsKey("dividendyield")) html.append("    <div class='dividend-yield'>Dividend Yield: ").append(String.format("%.2f%%", (Double) data.get("dividendyield") * 100)).append("</div>\n");
        if (data.containsKey("payoutratio")) html.append("    <div class='payout-ratio'>Payout Ratio: ").append(String.format("%.2f%%", (Double) data.get("payoutratio") * 100)).append("</div>\n");
        html.append("  </div>\n");

        // ============ GRAHAM & FAIR VALUE SECTION ============
        html.append("  <div class='section fair-value'>\n");
        html.append("    <h3>Fair Value & Graham</h3>\n");
        html.append("    <div class='graham-fair-value'>Graham Fair Value: To be calculated</div>\n");
        html.append("    <div class='margin-of-safety'>Margin of Safety: To be calculated</div>\n");
        html.append("  </div>\n");

        html.append("</div>");

        return html.toString();
    }

    private String formatNumber(Double value) {
        if (value == null) return "N/A";
        if (value >= 1_000_000_000) {
            return String.format("%.2fB", value / 1_000_000_000);
        } else if (value >= 1_000_000) {
            return String.format("%.2fM", value / 1_000_000);
        } else if (value >= 1_000) {
            return String.format("%.2fK", value / 1_000);
        }
        return String.valueOf(value);
    }
}