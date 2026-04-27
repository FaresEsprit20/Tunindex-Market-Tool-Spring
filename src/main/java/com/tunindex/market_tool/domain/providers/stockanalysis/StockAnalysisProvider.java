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

    // Cache for stock data from list page
    private Map<String, Map<String, Object>> stockDataCache = new HashMap<>();

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

        // Fetch from cache or load list page
        return getStockDataFromCacheOrFetch(symbol)
                .map(data -> buildRawStockData(symbol, stockInfo, data))
                .map(dataParserService::parseToNormalized)
                .map(normalizer::toEntity)
                .flatMap(enricher::enrich)
                .doOnSuccess(data -> log.info("Successfully fetched and enriched data for {} from StockAnalysis", symbol))
                .doOnError(error -> log.error("Failed to fetch data for {}: {}", symbol, error.getMessage()));
    }

    private Mono<Map<String, Object>> getStockDataFromCacheOrFetch(String symbol) {
        if (stockDataCache.containsKey(symbol)) {
            return Mono.just(stockDataCache.get(symbol));
        }

        return fetchAllStocksData().flatMap(dataMap -> {
            stockDataCache.putAll(dataMap);
            if (dataMap.containsKey(symbol)) {
                return Mono.just(dataMap.get(symbol));
            }
            return Mono.error(new RuntimeException("Stock data not found for symbol: " + symbol));
        });
    }

    private Mono<Map<String, Map<String, Object>>> fetchAllStocksData() {
        String listUrl = Constants.STOCKANALYSIS_LIST_URL;

        return fetchPage(listUrl)
                .map(html -> extractAllStocksFromListPage(html))
                .doOnSuccess(map -> log.info("Loaded {} stocks from list page", map.size()));
    }

    private Map<String, Map<String, Object>> extractAllStocksFromListPage(String html) {
        Map<String, Map<String, Object>> result = new HashMap<>();

        try {
            // This pattern works on the LIST page, not individual stock pages
            Pattern pattern = Pattern.compile("stockData:\\s*(\\[\\{.*?\\}\\])", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String stockDataJson = matcher.group(1);
                JsonNode stockArray = objectMapper.readTree(stockDataJson);

                for (JsonNode stock : stockArray) {
                    String symbol = stock.has("s") ? stock.get("s").asText().replace("bvmt/", "") : "";

                    if (!symbol.isEmpty()) {
                        Map<String, Object> stockData = new HashMap<>();
                        Iterator<Map.Entry<String, JsonNode>> fields = stock.fields();
                        while (fields.hasNext()) {
                            Map.Entry<String, JsonNode> field = fields.next();
                            String fieldName = field.getKey();
                            JsonNode fieldValue = field.getValue();

                            if (fieldValue != null && !fieldValue.isNull()) {
                                if (fieldValue.isNumber()) {
                                    stockData.put(fieldName, fieldValue.asDouble());
                                } else if (fieldValue.isTextual()) {
                                    String textValue = fieldValue.asText();
                                    if (!textValue.isEmpty() && !textValue.equals("-")) {
                                        stockData.put(fieldName, textValue);
                                    }
                                } else if (fieldValue.isBoolean()) {
                                    stockData.put(fieldName, fieldValue.asBoolean());
                                } else if (fieldValue.isInt()) {
                                    stockData.put(fieldName, fieldValue.asInt());
                                }
                            }
                        }
                        result.put(symbol, stockData);
                    }
                }
            }

            log.info("Extracted {} stocks from list page", result.size());

        } catch (Exception e) {
            log.error("Failed to extract stocks from list page: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public Flux<EnrichedStockData> fetchAllStocks() {
        log.info("Fetching all stocks from StockAnalysis.com");

        return fetchAllStocksData()
                .flatMapMany(dataMap -> Flux.fromIterable(dataMap.keySet()))
                .flatMap(symbol -> fetchStockData(symbol)
                        .onErrorResume(error -> {
                            log.error("Failed to fetch {}: {}", symbol, error.getMessage());
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

        log.info("Built HTML for {} with data: {}", symbol, data.keySet());

        return rawData;
    }

    private String buildComprehensiveHtml(String symbol, Constants.StockInfo stockInfo, Map<String, Object> data) {
        BigDecimal currentPrice = data.containsKey("price") ? BigDecimal.valueOf((Double) data.get("price")) : null;
        BigDecimal week52High = data.containsKey("high52") ? BigDecimal.valueOf((Double) data.get("high52")) : null;
        BigDecimal week52Low = data.containsKey("low52") ? BigDecimal.valueOf((Double) data.get("low52")) : null;

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
        if (data.containsKey("marketCap")) html.append("    <div class='market-cap'>Market Cap: ").append(formatNumber((Double) data.get("marketCap"))).append("</div>\n");
        if (data.containsKey("price")) html.append("    <div class='price'>Price: ").append(data.get("price")).append("</div>\n");
        if (data.containsKey("change")) html.append("    <div class='change'>Change: ").append(data.get("change")).append("%</div>\n");
        if (data.containsKey("revenue")) html.append("    <div class='revenue'>Revenue: ").append(formatNumber((Double) data.get("revenue"))).append("</div>\n");
        html.append("  </div>\n");

        // Price Section
        html.append("  <div class='section price'>\n");
        html.append("    <h3>Price Information</h3>\n");
        if (data.containsKey("volume")) html.append("    <div class='volume'>Volume: ").append(data.get("volume")).append("</div>\n");
        if (data.containsKey("averageVolume")) html.append("    <div class='avg-volume'>Avg Volume: ").append(data.get("averageVolume")).append("</div>\n");
        if (week52Low != null) html.append("    <div class='week52-low'>52-Week Low: ").append(week52Low).append("</div>\n");
        if (week52High != null) html.append("    <div class='week52-high'>52-Week High: ").append(week52High).append("</div>\n");
        html.append("  </div>\n");

        // Ratios Section
        html.append("  <div class='section ratios'>\n");
        html.append("    <h3>Ratios</h3>\n");
        if (data.containsKey("peRatio")) html.append("    <div class='pe-ratio'>P/E Ratio: ").append(data.get("peRatio")).append("</div>\n");
        if (data.containsKey("pbRatio")) html.append("    <div class='pb-ratio'>P/B Ratio: ").append(data.get("pbRatio")).append("</div>\n");
        if (data.containsKey("psRatio")) html.append("    <div class='ps-ratio'>P/S Ratio: ").append(data.get("psRatio")).append("</div>\n");
        if (data.containsKey("dividendYield")) html.append("    <div class='dividend-yield'>Dividend Yield: ").append(data.get("dividendYield")).append("%</div>\n");
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