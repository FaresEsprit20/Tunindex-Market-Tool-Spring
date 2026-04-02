package com.tunindex.market_tool.domain.providers.investingcom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import com.tunindex.market_tool.core.exception.market.ParseException;
import java.util.Collections;

@Component
@Slf4j
public class InvestingComParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode parseNextDataScript(String html, String symbol) throws ParseException {
        try {
            Document doc = Jsoup.parse(html);
            var scriptTag = doc.selectFirst("script#__NEXT_DATA__");
            if (scriptTag == null) {
                throw new ParseException(
                        ErrorCodes.HTML_PARSE_ERROR,
                        "HTML",
                        "Could not find __NEXT_DATA__ script tag",
                        Collections.singletonList("Symbol: " + symbol)
                );
            }
            String jsonData = scriptTag.html();
            return objectMapper.readTree(jsonData);
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException(
                    ErrorCodes.JSON_PARSE_ERROR,
                    "HTML",
                    "Failed to parse __NEXT_DATA__: " + e.getMessage(),
                    Collections.singletonList("Symbol: " + symbol)
            );
        }
    }

    public Double extractTotalEquity(JsonNode data, String symbol) {
        try {
            JsonNode balanceSheet = data.at("/props/pageProps/state/balanceSheetStore/balanceSheetDataAnnual");
            if (balanceSheet.isMissingNode()) {
                log.debug("Balance sheet data not found for symbol: {}", symbol);
                return null;
            }

            JsonNode reports = balanceSheet.get("reports");
            if (reports == null || !reports.isArray() || reports.isEmpty()) {
                log.debug("No balance sheet reports found for symbol: {}", symbol);
                return null;
            }

            JsonNode latestReport = reports.get(reports.size() - 1);
            JsonNode indicators = latestReport.get("indicators");
            if (indicators == null) {
                return null;
            }

            JsonNode totalEquity = indicators.get("total_equity_standard");
            if (totalEquity == null) {
                totalEquity = indicators.get("total_equity");
            }

            if (totalEquity != null && totalEquity.has("value")) {
                double value = totalEquity.get("value").asDouble();
                log.debug("Extracted Total Equity for {}: {} million", symbol, value);
                return value;
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to extract Total Equity for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public Double extractNetIncome(JsonNode data, String symbol) {
        try {
            JsonNode incomeStatement = data.at("/props/pageProps/state/incomeStatementStore/incomeStatementDataAnnual");
            if (incomeStatement.isMissingNode()) {
                return null;
            }

            JsonNode reports = incomeStatement.get("reports");
            if (reports == null || !reports.isArray() || reports.isEmpty()) {
                return null;
            }

            JsonNode latestReport = reports.get(reports.size() - 1);
            JsonNode indicators = latestReport.get("indicators");
            if (indicators == null) {
                return null;
            }

            JsonNode netIncome = indicators.get("net_income");
            if (netIncome != null && netIncome.has("value")) {
                double value = netIncome.get("value").asDouble();
                log.debug("Extracted Net Income for {}: {} million", symbol, value);
                return value;
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to extract Net Income for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public Double extractTotalRevenue(JsonNode data, String symbol) {
        try {
            JsonNode incomeStatement = data.at("/props/pageProps/state/incomeStatementStore/incomeStatementDataAnnual");
            if (incomeStatement.isMissingNode()) {
                return null;
            }

            JsonNode reports = incomeStatement.get("reports");
            if (reports == null || !reports.isArray() || reports.isEmpty()) {
                return null;
            }

            JsonNode latestReport = reports.get(reports.size() - 1);
            JsonNode indicators = latestReport.get("indicators");
            if (indicators == null) {
                return null;
            }

            JsonNode revenue = indicators.get("total_revenues_standard");
            if (revenue != null && revenue.has("value")) {
                double value = revenue.get("value").asDouble();
                log.debug("Extracted Total Revenue for {}: {} million", symbol, value);
                return value;
            }

            return null;
        } catch (Exception e) {
            log.warn("Failed to extract Total Revenue for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public Double extractSharesOutstanding(JsonNode data, String symbol) {
        try {
            JsonNode fundamental = data.at("/props/pageProps/state/equityStore/instrument/fundamental");
            if (!fundamental.isMissingNode() && fundamental.has("sharesOutstanding")) {
                double value = fundamental.get("sharesOutstanding").asDouble();
                log.debug("Extracted Shares Outstanding for {}: {}", symbol, value);
                return value;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract Shares Outstanding for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public Double extractEps(JsonNode data, String symbol) {
        try {
            JsonNode fundamental = data.at("/props/pageProps/state/equityStore/instrument/fundamental");
            if (!fundamental.isMissingNode() && fundamental.has("eps")) {
                double value = fundamental.get("eps").asDouble();
                log.debug("Extracted EPS for {}: {}", symbol, value);
                return value;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract EPS for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    public Double extractCurrentPrice(JsonNode data, String symbol) {
        try {
            JsonNode price = data.at("/props/pageProps/state/equityStore/instrument/price");
            if (!price.isMissingNode() && price.has("last")) {
                double value = price.get("last").asDouble();
                log.debug("Extracted Current Price for {}: {}", symbol, value);
                return value;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract Current Price for {}: {}", symbol, e.getMessage());
            return null;
        }
    }
}