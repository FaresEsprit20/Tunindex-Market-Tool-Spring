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

        // Extract all metrics from the HTML
        Map<String, String> extractedData = extractMetricsFromHtml(html);

        // Build HTML with extracted data for the parser
        String combinedHtml = buildHtmlWithMetrics(symbol, stockInfo, extractedData);

        rawData.setMainPageHtml(combinedHtml);
        rawData.setBalanceSheetHtml(combinedHtml);
        rawData.setIncomeStatementHtml(combinedHtml);
        rawData.setFinancialSummaryHtml(combinedHtml);

        return rawData;
    }

    private Map<String, String> extractMetricsFromHtml(String html) {
        Map<String, String> metrics = new HashMap<>();

        try {
            // Extract Market Cap
            String marketCap = extractValue(html, "Market Cap\\s*\\|\\s*([0-9.]+[MB]?)");
            if (marketCap != null) metrics.put("marketCap", marketCap);

            // Extract Revenue
            String revenue = extractValue(html, "Revenue \\(ttm\\)\\s*\\|\\s*([0-9.]+[MB]?)");
            if (revenue != null) metrics.put("revenue", revenue);

            // Extract Net Income
            String netIncome = extractValue(html, "Net Income\\s*\\|\\s*([0-9.]+[MB]?)");
            if (netIncome != null) metrics.put("netIncome", netIncome);

            // Extract EPS
            String eps = extractValue(html, "EPS\\s*\\|\\s*([0-9.]+)");
            if (eps != null) metrics.put("eps", eps);

            // Extract Shares Outstanding
            String sharesOut = extractValue(html, "Shares Out\\s*\\|\\s*([0-9.]+[MB]?)");
            if (sharesOut != null) metrics.put("sharesOutstanding", sharesOut);

            // Extract PE Ratio
            String peRatio = extractValue(html, "PE Ratio\\s*\\|\\s*([0-9.]+)");
            if (peRatio != null) metrics.put("peRatio", peRatio);

            // Extract Forward PE
            String forwardPe = extractValue(html, "Forward PE\\s*\\|\\s*([0-9.]+)");
            if (forwardPe != null) metrics.put("forwardPE", forwardPe);

            // Extract Dividend
            String dividend = extractValue(html, "Dividend\\s*\\|\\s*([0-9.]+)\\s*\\(([0-9.]+)%\\)");
            if (dividend != null) {
                metrics.put("dividend", dividend);
                String divYield = extractValue(html, "Dividend\\s*\\|\\s*[0-9.]+\\s*\\(([0-9.]+)%\\)");
                if (divYield != null) metrics.put("dividendYield", divYield);
            }

            // Extract Volume
            String volume = extractValue(html, "Volume\\s*\\|\\s*([0-9,]+)");
            if (volume != null) metrics.put("volume", volume.replace(",", ""));

            // Extract Average Volume
            String avgVolume = extractValue(html, "Average Volume\\s*\\|\\s*([0-9,]+)");
            if (avgVolume != null) metrics.put("averageVolume", avgVolume.replace(",", ""));

            // Extract Open
            String open = extractValue(html, "Open\\s*\\|\\s*([0-9.]+)");
            if (open != null) metrics.put("open", open);

            // Extract Previous Close
            String prevClose = extractValue(html, "Previous Close\\s*\\|\\s*([0-9.]+)");
            if (prevClose != null) metrics.put("prevClose", prevClose);

            // Extract Day's Range
            String dayRange = extractValue(html, "Day's Range\\s*\\|\\s*([0-9.]+)\\s*-\\s*([0-9.]+)");
            if (dayRange != null) {
                metrics.put("dayLow", extractValue(html, "Day's Range\\s*\\|\\s*([0-9.]+)\\s*-"));
                metrics.put("dayHigh", extractValue(html, "Day's Range\\s*\\|\\s*[0-9.]+\\s*-\\s*([0-9.]+)"));
            }

            // Extract 52-Week Range
            String week52Range = extractValue(html, "52-Week Range\\s*\\|\\s*([0-9.]+)\\s*-\\s*([0-9.]+)");
            if (week52Range != null) {
                metrics.put("week52Low", extractValue(html, "52-Week Range\\s*\\|\\s*([0-9.]+)\\s*-"));
                metrics.put("week52High", extractValue(html, "52-Week Range\\s*\\|\\s*[0-9.]+\\s*-\\s*([0-9.]+)"));
                metrics.put("week52Range", week52Range);
            }

            // Extract Beta
            String beta = extractValue(html, "Beta\\s*\\|\\s*([0-9.]+)");
            if (beta != null) metrics.put("beta", beta);

            // Extract RSI
            String rsi = extractValue(html, "RSI\\s*\\|\\s*([0-9.]+)");
            if (rsi != null) metrics.put("rsi", rsi);

            // Extract price and change from the top of the page
            String price = extractValue(html, "([0-9.]+)\\s*\\n\\s*[-+][0-9.]+\\s*\\([-+][0-9.]+%\\)");
            if (price != null) metrics.put("price", price);

            String change = extractValue(html, "[0-9.]+\\s*\\n\\s*([-+][0-9.]+)\\s*\\([-+][0-9.]+%\\)");
            if (change != null) metrics.put("change", change);

            String changePct = extractValue(html, "[0-9.]+\\s*\\n\\s*[-+][0-9.]+\\s*\\(([-+][0-9.]+)%\\)");
            if (changePct != null) metrics.put("changePct", changePct);

            // Extract 1Y Return
            String oneYearReturn = extractValue(html, "([0-9.]+)%\\s*\\(1Y\\)");
            if (oneYearReturn != null) metrics.put("oneYearReturn", oneYearReturn);

        } catch (Exception e) {
            log.warn("Error extracting metrics from HTML: {}", e.getMessage());
        }

        return metrics;
    }

    private String extractValue(String html, String pattern) {
        try {
            Pattern p = Pattern.compile(pattern, Pattern.DOTALL);
            Matcher m = p.matcher(html);
            if (m.find()) {
                return m.group(1).trim();
            }
        } catch (Exception e) {
            log.debug("Failed to extract pattern: {}", pattern);
        }
        return null;
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
        if (stockInfo.ownershipType() != null) {
            html.append("  <div class='ownership-type'>Ownership: ").append(stockInfo.ownershipType()).append("</div>\n");
        }

        // Overview Section
        html.append("  <div class='section overview'>\n");
        html.append("    <h3>Overview</h3>\n");
        if (metrics.containsKey("marketCap")) html.append("    <div class='market-cap'>Market Cap: ").append(metrics.get("marketCap")).append("</div>\n");
        if (metrics.containsKey("price")) html.append("    <div class='price'>Price: ").append(metrics.get("price")).append("</div>\n");
        if (metrics.containsKey("changePct")) html.append("    <div class='change'>Change: ").append(metrics.get("changePct")).append("%</div>\n");
        if (metrics.containsKey("revenue")) html.append("    <div class='revenue'>Revenue: ").append(metrics.get("revenue")).append("</div>\n");
        if (metrics.containsKey("netIncome")) html.append("    <div class='net-income'>Net Income: ").append(metrics.get("netIncome")).append("</div>\n");
        if (metrics.containsKey("eps")) html.append("    <div class='eps'>EPS: ").append(metrics.get("eps")).append("</div>\n");
        if (metrics.containsKey("sharesOutstanding")) html.append("    <div class='shares-outstanding'>Shares Outstanding: ").append(metrics.get("sharesOutstanding")).append("</div>\n");
        if (metrics.containsKey("oneYearReturn")) html.append("    <div class='one-year-return'>1Y Return: ").append(metrics.get("oneYearReturn")).append("%</div>\n");
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
        html.append("  </div>\n");

        // Price & Volume Section
        html.append("  <div class='section price-volume'>\n");
        html.append("    <h3>Price & Volume</h3>\n");
        if (metrics.containsKey("volume")) html.append("    <div class='volume'>Volume: ").append(metrics.get("volume")).append("</div>\n");
        if (metrics.containsKey("averageVolume")) html.append("    <div class='avg-volume'>Average Volume: ").append(metrics.get("averageVolume")).append("</div>\n");
        if (metrics.containsKey("open")) html.append("    <div class='open'>Open: ").append(metrics.get("open")).append("</div>\n");
        if (metrics.containsKey("prevClose")) html.append("    <div class='prev-close'>Previous Close: ").append(metrics.get("prevClose")).append("</div>\n");
        if (metrics.containsKey("dayLow")) html.append("    <div class='day-low'>Day Low: ").append(metrics.get("dayLow")).append("</div>\n");
        if (metrics.containsKey("dayHigh")) html.append("    <div class='day-high'>Day High: ").append(metrics.get("dayHigh")).append("</div>\n");
        if (metrics.containsKey("week52Low")) html.append("    <div class='week52-low'>52-Week Low: ").append(metrics.get("week52Low")).append("</div>\n");
        if (metrics.containsKey("week52High")) html.append("    <div class='week52-high'>52-Week High: ").append(metrics.get("week52High")).append("</div>\n");
        if (metrics.containsKey("week52Range")) html.append("    <div class='week52-range'>52-Week Range: ").append(metrics.get("week52Range")).append("</div>\n");
        html.append("  </div>\n");

        // Technical Section
        html.append("  <div class='section technical'>\n");
        html.append("    <h3>Technical</h3>\n");
        if (metrics.containsKey("beta")) html.append("    <div class='beta'>Beta: ").append(metrics.get("beta")).append("</div>\n");
        if (metrics.containsKey("rsi")) html.append("    <div class='rsi'>RSI: ").append(metrics.get("rsi")).append("</div>\n");
        html.append("  </div>\n");

        html.append("</div>");

        return html.toString();
    }
}