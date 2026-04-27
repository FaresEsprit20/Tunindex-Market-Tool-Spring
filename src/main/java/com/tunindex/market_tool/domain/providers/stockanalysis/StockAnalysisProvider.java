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

        String url = Constants.STOCKANALYSIS_BASE_URL + symbol + "/";

        return fetchPage(url)
                .map(html -> extractStockDataFromPage(html, symbol, stockInfo))
                .map(dataParserService::parseToNormalized)
                .map(normalizer::toEntity)
                .flatMap(enricher::enrich)
                .doOnSuccess(data -> log.info("Successfully fetched and enriched data for {} from StockAnalysis", symbol))
                .doOnError(error -> log.error("Failed to fetch data for {}: {}", symbol, error.getMessage()));
    }

    @Override
    public Flux<EnrichedStockData> fetchAllStocks() {
        log.info("Fetching all stocks from StockAnalysis.com");

        // Use the pre-configured stock list from Constants
        Map<String, Constants.StockInfo> stocks = Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS;

        return Flux.fromIterable(stocks.entrySet())
                .flatMap(entry -> fetchStockData(entry.getKey())
                        .onErrorResume(error -> {
                            log.error("Failed to fetch {}: {}", entry.getKey(), error.getMessage());
                            return Mono.empty();
                        }), 5) // Process 5 stocks concurrently
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

    private RawStockData extractStockDataFromPage(String html, String symbol, Constants.StockInfo stockInfo) {
        RawStockData rawData = new RawStockData();
        rawData.setSymbol(symbol);
        rawData.setStockInfo(stockInfo);

        try {
            // Store the HTML for parsing
            rawData.setMainPageHtml(html);

            // Extract stock data from the embedded JSON
            Map<String, Object> stockDataMap = extractStockDataFromJson(html);

            // Build HTML with extracted data for the parser
            StringBuilder financialHtml = new StringBuilder();
            financialHtml.append("<div class='stock-data'>\n");
            financialHtml.append("  <div class='symbol'>").append(symbol).append("</div>\n");
            financialHtml.append("  <div class='company-name'>").append(stockInfo.getName()).append("</div>\n");

            if (stockDataMap.containsKey("marketCap")) {
                financialHtml.append("  <div class='market-cap'>").append(stockDataMap.get("marketCap")).append("</div>\n");
            }
            if (stockDataMap.containsKey("price")) {
                financialHtml.append("  <div class='price'>").append(stockDataMap.get("price")).append("</div>\n");
            }
            if (stockDataMap.containsKey("change")) {
                financialHtml.append("  <div class='change'>").append(stockDataMap.get("change")).append("</div>\n");
            }
            if (stockDataMap.containsKey("revenue")) {
                financialHtml.append("  <div class='revenue'>").append(stockDataMap.get("revenue")).append("</div>\n");
            }

            financialHtml.append("</div>");
            String combinedHtml = financialHtml.toString();

            // Set all pages with the extracted data
            rawData.setBalanceSheetHtml(combinedHtml);
            rawData.setIncomeStatementHtml(combinedHtml);
            rawData.setFinancialSummaryHtml(combinedHtml);

            log.info("Successfully extracted data for {}: Market Cap={}, Price={}, Change={}%, Revenue={}",
                    symbol,
                    stockDataMap.getOrDefault("marketCap", "N/A"),
                    stockDataMap.getOrDefault("price", "N/A"),
                    stockDataMap.getOrDefault("change", "N/A"),
                    stockDataMap.getOrDefault("revenue", "N/A"));

        } catch (Exception e) {
            log.error("Failed to extract stock data from page for {}: {}", symbol, e.getMessage());
        }

        return rawData;
    }

    private Map<String, Object> extractStockDataFromJson(String html) {
        Map<String, Object> data = new HashMap<>();

        try {
            // Pattern to find stockData array in the page
            Pattern pattern = Pattern.compile("stockData:\\s*(\\[\\{.*?\\}\\])", Pattern.DOTALL);
            Matcher matcher = pattern.matcher(html);

            if (matcher.find()) {
                String stockDataJson = matcher.group(1);
                JsonNode stockArray = objectMapper.readTree(stockDataJson);

                if (stockArray.isArray() && stockArray.size() > 0) {
                    JsonNode stock = stockArray.get(0);

                    // Extract market cap (convert from millions/billions format)
                    if (stock.has("marketCap")) {
                        JsonNode marketCapNode = stock.get("marketCap");
                        if (marketCapNode.isNumber()) {
                            data.put("marketCap", marketCapNode.asDouble());
                        } else {
                            String marketCapStr = marketCapNode.asText();
                            data.put("marketCap", parseNumericValue(marketCapStr));
                        }
                    }

                    // Extract price
                    if (stock.has("price")) {
                        data.put("price", stock.get("price").asDouble());
                    }

                    // Extract change percentage
                    if (stock.has("change")) {
                        data.put("change", stock.get("change").asDouble());
                    }

                    // Extract revenue
                    if (stock.has("revenue")) {
                        JsonNode revenueNode = stock.get("revenue");
                        if (revenueNode != null && !revenueNode.isNull()) {
                            if (revenueNode.isNumber()) {
                                data.put("revenue", revenueNode.asDouble());
                            } else {
                                String revenueStr = revenueNode.asText();
                                if (!revenueStr.equals("-") && !revenueStr.isEmpty()) {
                                    data.put("revenue", parseNumericValue(revenueStr));
                                }
                            }
                        }
                    }

                    // Extract company name
                    if (stock.has("n")) {
                        data.put("companyName", stock.get("n").asText());
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to extract JSON data: {}", e.getMessage());
        }

        return data;
    }

    /**
     * Parse numeric values like "5.92B", "1.54B", "714.71M" to double
     */
    private double parseNumericValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }

        try {
            value = value.trim().toUpperCase();
            double multiplier = 1.0;

            if (value.endsWith("B")) {
                multiplier = 1_000_000_000.0;
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("M")) {
                multiplier = 1_000_000.0;
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("K")) {
                multiplier = 1_000.0;
                value = value.substring(0, value.length() - 1);
            }

            double number = Double.parseDouble(value);
            return number * multiplier;

        } catch (NumberFormatException e) {
            log.warn("Failed to parse numeric value: {}", value);
            return 0.0;
        }
    }
}