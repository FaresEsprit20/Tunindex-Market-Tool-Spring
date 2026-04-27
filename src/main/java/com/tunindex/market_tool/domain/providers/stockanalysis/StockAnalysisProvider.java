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

        // Fetch overview, ratios, and statistics pages
        return Mono.zip(
                        fetchPage(overviewUrl),
                        fetchPage(ratiosUrl),
                        fetchPage(statisticsUrl)
                )
                .map(tuple -> {
                    String overviewHtml = tuple.getT1();
                    String ratiosHtml = tuple.getT2();
                    String statisticsHtml = tuple.getT3();

                    // Extract data from all pages
                    Map<String, Object> overviewData = extractAllStockDataFromJson(overviewHtml, symbol);
                    Map<String, Object> ratiosData = extractRatiosDataFromJson(ratiosHtml, symbol);
                    Map<String, Object> statisticsData = extractStatisticsDataFromJson(statisticsHtml, symbol);

                    // Merge data (later sources take precedence)
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

        // Build comprehensive HTML with ALL extracted data
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

    /**
     * Extract ratio data from the financial ratios page
     * Always takes the latest data (Current/TTM column or most recent fiscal year)
     */
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

                    // Extract only the metrics we need from ratios page
                    extractRatioMetric(financialData, "pe", latestColumnIndex, data);
                    extractRatioMetric(financialData, "pb", latestColumnIndex, data);
                    extractRatioMetric(financialData, "dividendyield", latestColumnIndex, data);
                    extractRatioMetric(financialData, "debtequity", latestColumnIndex, data);
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract ratios data for {}: {}", targetSymbol, e.getMessage());
        }

        return data;
    }

    /**
     * Extract statistics data from the statistics page
     * Extracts Profit Margin and Book Value Per Share (BVPS) from the latest data
     */
    private Map<String, Object> extractStatisticsDataFromJson(String html, String targetSymbol) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Look for profitMargin in the margins section
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

            // Look for Book Value Per Share in the balance sheet section
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

            // Alternative: Look for the structured data in the statistics page
            // The page contains a data object with all statistics
            Pattern statisticsPattern = Pattern.compile("\"margins\":\\s*\\{[^}]*\"data\":\\s*\\[([^\\]]+)\\]", Pattern.DOTALL);
            Matcher statisticsMatcher = statisticsPattern.matcher(html);

            // Also try to find profitMargin from the text content as fallback
            if (!data.containsKey("profitMargin")) {
                Pattern profitMarginTextPattern = Pattern.compile("Profit Margin</div>\\s*<div[^>]*>\\s*([0-9.]+)%", Pattern.DOTALL);
                Matcher textMatcher = profitMarginTextPattern.matcher(html);
                if (textMatcher.find()) {
                    try {
                        data.put("profitMargin", Double.parseDouble(textMatcher.group(1)));
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse profitMargin from text: {}", textMatcher.group(1));
                    }
                }
            }

            // Also try to find Book Value Per Share from text content as fallback
            if (!data.containsKey("bookValuePerShare")) {
                Pattern bvpsTextPattern = Pattern.compile("Book Value Per Share</div>\\s*<div[^>]*>\\s*([0-9.]+)", Pattern.DOTALL);
                Matcher textMatcher = bvpsTextPattern.matcher(html);
                if (textMatcher.find()) {
                    try {
                        data.put("bookValuePerShare", Double.parseDouble(textMatcher.group(1)));
                    } catch (NumberFormatException e) {
                        log.debug("Failed to parse bookValuePerShare from text: {}", textMatcher.group(1));
                    }
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
        StringBuilder html = new StringBuilder();
        html.append("<div class='stock-analysis-data'>\n");
        html.append("  <div class='symbol'>").append(symbol).append("</div>\n");
        html.append("  <div class='company-name'>").append(stockInfo.name()).append("</div>\n");

        // Add Industry and Country from stockInfo record
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
        if (data.containsKey("lastCloseRatios")) html.append("    <div class='last-close'>Last Close: ").append(data.get("lastCloseRatios")).append("</div>\n");
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
        html.append("  </div>\n");

        // ============ PROFITABILITY & MARGINS SECTION ============
        html.append("  <div class='section profitability'>\n");
        html.append("    <h3>Profitability & Margins</h3>\n");
        if (data.containsKey("profitMargin")) html.append("    <div class='profit-margin'>Profit Margin: ").append(String.format("%.2f%%", (Double) data.get("profitMargin"))).append("</div>\n");
        if (data.containsKey("operatingMargin")) html.append("    <div class='operating-margin'>Operating Margin: ").append(String.format("%.2f%%", (Double) data.get("operatingMargin") * 100)).append("</div>\n");
        html.append("  </div>\n");

        // ============ DIVIDENDS SECTION ============
        html.append("  <div class='section dividends'>\n");
        html.append("    <h3>Dividends</h3>\n");
        if (data.containsKey("dividendyield")) html.append("    <div class='dividend-yield'>Dividend Yield: ").append(String.format("%.2f%%", (Double) data.get("dividendyield") * 100)).append("</div>\n");
        if (data.containsKey("payoutratio")) html.append("    <div class='payout-ratio'>Payout Ratio: ").append(String.format("%.2f%%", (Double) data.get("payoutratio") * 100)).append("</div>\n");
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