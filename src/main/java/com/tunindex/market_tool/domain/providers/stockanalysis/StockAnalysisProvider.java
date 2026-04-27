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

        Map<String, Constants.StockInfo> stocks = Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS;

        return Flux.fromIterable(stocks.entrySet())
                .flatMap(entry -> fetchStockData(entry.getKey())
                        .onErrorResume(error -> {
                            log.error("Failed to fetch {}: {}", entry.getKey(), error.getMessage());
                            return Mono.empty();
                        }), 5)
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
            rawData.setMainPageHtml(html);
            Map<String, Object> stockDataMap = extractAllStockDataFromJson(html, symbol);

            // Build comprehensive HTML with ALL extracted data including price fields
            String combinedHtml = buildComprehensiveHtml(symbol, stockInfo, stockDataMap);

            rawData.setBalanceSheetHtml(combinedHtml);
            rawData.setIncomeStatementHtml(combinedHtml);
            rawData.setFinancialSummaryHtml(combinedHtml);

            log.info("Successfully extracted data for {}: {}", symbol, stockDataMap.keySet());

        } catch (Exception e) {
            log.error("Failed to extract stock data from page for {}: {}", symbol, e.getMessage());
        }

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
                        // Extract ALL available fields
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
            log.error("Failed to extract JSON data for {}: {}", targetSymbol, e.getMessage());
        }

        return data;
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
        if (data.containsKey("peRatio")) html.append("    <div class='pe-ratio'>P/E Ratio: ").append(data.get("peRatio")).append("</div>\n");
        if (data.containsKey("dividendYield")) html.append("    <div class='dividend-yield'>Dividend Yield: ").append(data.get("dividendYield")).append("%</div>\n");
        if (data.containsKey("eps")) html.append("    <div class='eps'>EPS: ").append(data.get("eps")).append("</div>\n");
        if (data.containsKey("pbRatio")) html.append("    <div class='pb-ratio'>P/B Ratio: ").append(data.get("pbRatio")).append("</div>\n");
        if (data.containsKey("psRatio")) html.append("    <div class='ps-ratio'>P/S Ratio: ").append(data.get("psRatio")).append("</div>\n");
        if (data.containsKey("profitMargin")) html.append("    <div class='profit-margin'>Profit Margin: ").append(data.get("profitMargin")).append("%</div>\n");
        html.append("  </div>\n");

        // ============ PERFORMANCE SECTION ============
        html.append("  <div class='section performance'>\n");
        html.append("    <h3>Performance</h3>\n");
        if (data.containsKey("ch1w")) html.append("    <div class='ch1w'>1 Week: ").append(data.get("ch1w")).append("%</div>\n");
        if (data.containsKey("ch1m")) html.append("    <div class='ch1m'>1 Month: ").append(data.get("ch1m")).append("%</div>\n");
        if (data.containsKey("ch3m")) html.append("    <div class='ch3m'>3 Months: ").append(data.get("ch3m")).append("%</div>\n");
        if (data.containsKey("ch6m")) html.append("    <div class='ch6m'>6 Months: ").append(data.get("ch6m")).append("%</div>\n");
        if (data.containsKey("chYTD")) html.append("    <div class='chYTD'>Year to Date: ").append(data.get("chYTD")).append("%</div>\n");
        if (data.containsKey("ch1y")) html.append("    <div class='ch1y'>1 Year: ").append(data.get("ch1y")).append("%</div>\n");
        if (data.containsKey("ch3y")) html.append("    <div class='ch3y'>3 Years: ").append(data.get("ch3y")).append("%</div>\n");
        if (data.containsKey("ch5y")) html.append("    <div class='ch5y'>5 Years: ").append(data.get("ch5y")).append("%</div>\n");
        if (data.containsKey("ch10y")) html.append("    <div class='ch10y'>10 Years: ").append(data.get("ch10y")).append("%</div>\n");
        html.append("  </div>\n");

        // ============ TOTAL RETURN SECTION ============
        html.append("  <div class='section total-return'>\n");
        html.append("    <h3>Total Return</h3>\n");
        if (data.containsKey("tr1m")) html.append("    <div class='tr1m'>1 Month: ").append(data.get("tr1m")).append("%</div>\n");
        if (data.containsKey("tr6m")) html.append("    <div class='tr6m'>6 Months: ").append(data.get("tr6m")).append("%</div>\n");
        if (data.containsKey("trYTD")) html.append("    <div class='trYTD'>Year to Date: ").append(data.get("trYTD")).append("%</div>\n");
        if (data.containsKey("tr1y")) html.append("    <div class='tr1y'>1 Year: ").append(data.get("tr1y")).append("%</div>\n");
        if (data.containsKey("tr5y")) html.append("    <div class='tr5y'>5 Years: ").append(data.get("tr5y")).append("%</div>\n");
        if (data.containsKey("tr10y")) html.append("    <div class='tr10y'>10 Years: ").append(data.get("tr10y")).append("%</div>\n");
        html.append("  </div>\n");

        // ============ PRICE SECTION (ALL PRICE FIELDS) ============
        html.append("  <div class='section price'>\n");
        html.append("    <h3>Price Information</h3>\n");

        // Volume
        if (data.containsKey("volume")) html.append("    <div class='volume'>Volume: ").append(data.get("volume")).append("</div>\n");
        if (data.containsKey("averageVolume")) html.append("    <div class='avg-volume'>Average Volume: ").append(data.get("averageVolume")).append("</div>\n");
        if (data.containsKey("relativeVolume")) html.append("    <div class='relative-volume'>Relative Volume: ").append(data.get("relativeVolume")).append("</div>\n");

        // 52-week range
        if (data.containsKey("low52")) html.append("    <div class='low52'>52 Week Low: ").append(data.get("low52")).append("</div>\n");
        if (data.containsKey("high52")) html.append("    <div class='high52'>52 Week High: ").append(data.get("high52")).append("</div>\n");
        if (data.containsKey("low52ch")) html.append("    <div class='low52ch'>52 Week Low Change: ").append(data.get("low52ch")).append("%</div>\n");
        if (data.containsKey("high52ch")) html.append("    <div class='high52ch'>52 Week High Change: ").append(data.get("high52ch")).append("%</div>\n");
        if (data.containsKey("high52Date")) html.append("    <div class='high52-date'>52 Week High Date: ").append(data.get("high52Date")).append("</div>\n");
        if (data.containsKey("low52Date")) html.append("    <div class='low52-date'>52 Week Low Date: ").append(data.get("low52Date")).append("</div>\n");

        // All-time range
        if (data.containsKey("allTimeHigh")) html.append("    <div class='all-time-high'>All-Time High: ").append(data.get("allTimeHigh")).append("</div>\n");
        if (data.containsKey("allTimeHighChange")) html.append("    <div class='all-time-high-change'>All-Time High Change: ").append(data.get("allTimeHighChange")).append("%</div>\n");
        if (data.containsKey("allTimeHighDate")) html.append("    <div class='all-time-high-date'>All-Time High Date: ").append(data.get("allTimeHighDate")).append("</div>\n");
        if (data.containsKey("allTimeLow")) html.append("    <div class='all-time-low'>All-Time Low: ").append(data.get("allTimeLow")).append("</div>\n");
        if (data.containsKey("allTimeLowChange")) html.append("    <div class='all-time-low-change'>All-Time Low Change: ").append(data.get("allTimeLowChange")).append("%</div>\n");
        if (data.containsKey("allTimeLowDate")) html.append("    <div class='all-time-low-date'>All-Time Low Date: ").append(data.get("allTimeLowDate")).append("</div>\n");

        // Beta and volatility
        if (data.containsKey("beta")) html.append("    <div class='beta'>Beta: ").append(data.get("beta")).append("</div>\n");
        if (data.containsKey("beta1y")) html.append("    <div class='beta1y'>Beta (1Y): ").append(data.get("beta1y")).append("</div>\n");
        if (data.containsKey("beta2y")) html.append("    <div class='beta2y'>Beta (2Y): ").append(data.get("beta2y")).append("</div>\n");

        // Other price metrics
        if (data.containsKey("open")) html.append("    <div class='open'>Open: ").append(data.get("open")).append("</div>\n");
        if (data.containsKey("close")) html.append("    <div class='close'>Previous Close: ").append(data.get("close")).append("</div>\n");
        if (data.containsKey("low")) html.append("    <div class='low'>Day Low: ").append(data.get("low")).append("</div>\n");
        if (data.containsKey("high")) html.append("    <div class='high'>Day High: ").append(data.get("high")).append("</div>\n");
        if (data.containsKey("priceDate")) html.append("    <div class='price-date'>Price Date: ").append(data.get("priceDate")).append("</div>\n");

        // Technical indicators
        if (data.containsKey("rsi")) html.append("    <div class='rsi'>RSI: ").append(data.get("rsi")).append("</div>\n");
        if (data.containsKey("rsiWeekly")) html.append("    <div class='rsi-weekly'>Weekly RSI: ").append(data.get("rsiWeekly")).append("</div>\n");
        if (data.containsKey("rsiMonthly")) html.append("    <div class='rsi-monthly'>Monthly RSI: ").append(data.get("rsiMonthly")).append("</div>\n");
        if (data.containsKey("atr")) html.append("    <div class='atr'>ATR: ").append(data.get("atr")).append("</div>\n");

        html.append("  </div>\n");

        // ============ DIVIDENDS SECTION ============
        html.append("  <div class='section dividends'>\n");
        html.append("    <h3>Dividends</h3>\n");
        if (data.containsKey("dps")) html.append("    <div class='dps'>Dividend Per Share: ").append(data.get("dps")).append("</div>\n");
        if (data.containsKey("dividendGrowth")) html.append("    <div class='dividend-growth'>Dividend Growth: ").append(data.get("dividendGrowth")).append("%</div>\n");
        if (data.containsKey("payoutRatio")) html.append("    <div class='payout-ratio'>Payout Ratio: ").append(data.get("payoutRatio")).append("%</div>\n");
        if (data.containsKey("exDivDate")) html.append("    <div class='ex-div-date'>Ex-Div Date: ").append(data.get("exDivDate")).append("</div>\n");
        html.append("  </div>\n");

        // ============ PROFITABILITY SECTION ============
        html.append("  <div class='section profitability'>\n");
        html.append("    <h3>Profitability</h3>\n");
        if (data.containsKey("roe")) html.append("    <div class='roe'>ROE: ").append(data.get("roe")).append("%</div>\n");
        if (data.containsKey("roa")) html.append("    <div class='roa'>ROA: ").append(data.get("roa")).append("%</div>\n");
        if (data.containsKey("roic")) html.append("    <div class='roic'>ROIC: ").append(data.get("roic")).append("%</div>\n");
        if (data.containsKey("grossMargin")) html.append("    <div class='gross-margin'>Gross Margin: ").append(data.get("grossMargin")).append("%</div>\n");
        if (data.containsKey("operatingMargin")) html.append("    <div class='operating-margin'>Operating Margin: ").append(data.get("operatingMargin")).append("%</div>\n");
        html.append("  </div>\n");

        // ============ VALUATION SECTION ============
        html.append("  <div class='section valuation'>\n");
        html.append("    <h3>Valuation</h3>\n");
        if (data.containsKey("enterpriseValue")) html.append("    <div class='enterprise-value'>Enterprise Value: ").append(formatNumber((Double) data.get("enterpriseValue"))).append("</div>\n");
        if (data.containsKey("peForward")) html.append("    <div class='pe-forward'>Forward P/E: ").append(data.get("peForward")).append("</div>\n");
        if (data.containsKey("pegRatio")) html.append("    <div class='peg-ratio'>PEG Ratio: ").append(data.get("pegRatio")).append("</div>\n");
        if (data.containsKey("evSales")) html.append("    <div class='ev-sales'>EV/Sales: ").append(data.get("evSales")).append("</div>\n");
        if (data.containsKey("evEbitda")) html.append("    <div class='ev-ebitda'>EV/EBITDA: ").append(data.get("evEbitda")).append("</div>\n");
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