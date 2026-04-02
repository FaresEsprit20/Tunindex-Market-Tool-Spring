package com.tunindex.market_tool.domain.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.core.exception.ErrorCodes;
import com.tunindex.market_tool.core.exception.market.ParseException;
import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.domain.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.services.normalizer.DataNormalizerService;
import com.tunindex.market_tool.domain.services.parser.DataParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataParserServiceImpl implements DataParserService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataNormalizerService normalizer;

    private static final Pattern RATIO_CELL_PATTERN = Pattern.compile("border-b border-\\[#e4eaf1\\] py-3\\.5");
    private static final Pattern LABEL_PATTERN = Pattern.compile("text-xs font-semibold");
    private static final Pattern VALUE_PATTERN = Pattern.compile("block text-sm");

    @Override
    public NormalizedStockData parseToNormalized(RawStockData rawData) {
        log.debug("Parsing raw data for symbol: {}", rawData.getSymbol());

        NormalizedStockData normalizedData = new NormalizedStockData();

        // Set basic info from stockInfo
        Constants.StockInfo stockInfo = rawData.getStockInfo();
        normalizedData.setSymbol(stockInfo.getSymbol());
        normalizedData.setName(stockInfo.getName());
        normalizedData.setUrl(stockInfo.getUrl());
        normalizedData.setOwnershipType(stockInfo.getOwnershipType());

        // Parse main page JSON
        if (rawData.getMainPageHtml() != null) {
            try {
                JsonNode data = parseNextDataScript(rawData.getMainPageHtml(), rawData.getSymbol());
                extractBasicInfo(data, normalizedData);
                extractPriceData(data, normalizedData);
                extractFundamentalData(data, normalizedData);
                extractTechnicalData(data, normalizedData);
                extractAnalystData(data, normalizedData);
            } catch (ParseException e) {
                log.warn("Failed to parse main page for {}: {}", rawData.getSymbol(), e.getMessage());
            }
        }

        // Parse balance sheet
        if (rawData.getBalanceSheetHtml() != null) {
            try {
                JsonNode data = parseNextDataScript(rawData.getBalanceSheetHtml(), rawData.getSymbol());
                extractBalanceSheetData(data, normalizedData);
            } catch (ParseException e) {
                log.warn("Failed to parse balance sheet for {}: {}", rawData.getSymbol(), e.getMessage());
            }
        }

        // Parse income statement
        if (rawData.getIncomeStatementHtml() != null) {
            try {
                JsonNode data = parseNextDataScript(rawData.getIncomeStatementHtml(), rawData.getSymbol());
                extractIncomeStatementData(data, normalizedData);
            } catch (ParseException e) {
                log.warn("Failed to parse income statement for {}: {}", rawData.getSymbol(), e.getMessage());
            }
        }

        // Parse financial ratios
        if (rawData.getFinancialSummaryHtml() != null) {
            extractFinancialRatios(rawData.getFinancialSummaryHtml(), normalizedData);
        }

        // Calculate 52-week range string
        if (normalizedData.getWeek52Low() != null && normalizedData.getWeek52High() != null) {
            normalizedData.setWeek52Range(normalizedData.getWeek52Low() + " - " + normalizedData.getWeek52High());
        }

        // Clean all numeric values
        return normalizer.normalize(normalizedData);
    }

    @Override
    public JsonNode parseNextDataScript(String html, String symbol) throws ParseException {
        try {
            Document doc = Jsoup.parse(html);
            Element scriptTag = doc.selectFirst("script#__NEXT_DATA__");
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

    @Override
    public void extractBasicInfo(JsonNode data, NormalizedStockData normalizedData) {
        try {
            JsonNode instrument = data.at("/props/pageProps/state/equityStore/instrument");
            JsonNode exchange = instrument.get("exchange");
            JsonNode underlying = instrument.get("underlying");
            JsonNode companyProfile = data.at("/props/pageProps/state/companyProfileStore/profile");

            if (exchange != null) {
                if (exchange.has("exchange")) {
                    normalizedData.setExchange(exchange.get("exchange").asText());
                }
                if (exchange.has("exchangeFullName")) {
                    normalizedData.setExchangeFullName(exchange.get("exchangeFullName").asText());
                }
                if (exchange.has("marketName")) {
                    normalizedData.setMarket(exchange.get("marketName").asText());
                }
            }

            if (underlying != null && underlying.has("isin")) {
                normalizedData.setIsin(underlying.get("isin").asText());
            }

            JsonNode price = instrument.get("price");
            if (price != null && price.has("currency")) {
                normalizedData.setCurrency(price.get("currency").asText());
            }

            if (companyProfile != null && !companyProfile.isMissingNode()) {
                JsonNode sector = companyProfile.get("sector");
                if (sector != null && sector.has("name")) {
                    normalizedData.setSector(sector.get("name").asText());
                }
                JsonNode industry = companyProfile.get("industry");
                if (industry != null && industry.has("name")) {
                    normalizedData.setIndustry(industry.get("name").asText());
                }
            }

            log.debug("Extracted basic info for {}", normalizedData.getSymbol());
        } catch (Exception e) {
            log.warn("Failed to extract basic info: {}", e.getMessage());
        }
    }

    @Override
    public void extractPriceData(JsonNode data, NormalizedStockData normalizedData) {
        try {
            JsonNode price = data.at("/props/pageProps/state/equityStore/instrument/price");
            if (price != null && !price.isMissingNode()) {
                if (price.has("last")) {
                    normalizedData.setLastPrice(new BigDecimal(price.get("last").asText()));
                }
                if (price.has("lastClose")) {
                    normalizedData.setPrevClose(new BigDecimal(price.get("lastClose").asText()));
                }
                if (price.has("open")) {
                    normalizedData.setOpen(new BigDecimal(price.get("open").asText()));
                }
                if (price.has("high")) {
                    normalizedData.setDayHigh(new BigDecimal(price.get("high").asText()));
                }
                if (price.has("low")) {
                    normalizedData.setDayLow(new BigDecimal(price.get("low").asText()));
                }
                if (price.has("change")) {
                    normalizedData.setChange(new BigDecimal(price.get("change").asText()));
                }
                if (price.has("changePcr")) {
                    normalizedData.setChangePct(new BigDecimal(price.get("changePcr").asText()));
                }
                if (price.has("fiftyTwoWeekHigh")) {
                    normalizedData.setWeek52High(new BigDecimal(price.get("fiftyTwoWeekHigh").asText()));
                }
                if (price.has("fiftyTwoWeekLow")) {
                    normalizedData.setWeek52Low(new BigDecimal(price.get("fiftyTwoWeekLow").asText()));
                }
                if (price.has("lastUpdateTime")) {
                    normalizedData.setLastUpdate(price.get("lastUpdateTime").asLong());
                }
            }

            JsonNode bidding = data.at("/props/pageProps/state/equityStore/instrument/bidding");
            if (bidding != null && !bidding.isMissingNode()) {
                if (bidding.has("bid")) {
                    normalizedData.setBid(new BigDecimal(bidding.get("bid").asText()));
                }
                if (bidding.has("ask")) {
                    normalizedData.setAsk(new BigDecimal(bidding.get("ask").asText()));
                }
            }

            log.debug("Extracted price data for {}", normalizedData.getSymbol());
        } catch (Exception e) {
            log.warn("Failed to extract price data: {}", e.getMessage());
        }
    }

    @Override
    public void extractFundamentalData(JsonNode data, NormalizedStockData normalizedData) {
        try {
            JsonNode fundamental = data.at("/props/pageProps/state/equityStore/instrument/fundamental");
            if (fundamental != null && !fundamental.isMissingNode()) {
                if (fundamental.has("marketCapRaw")) {
                    normalizedData.setMarketCap(new BigDecimal(fundamental.get("marketCapRaw").asText()));
                }
                if (fundamental.has("sharesOutstanding")) {
                    normalizedData.setSharesOutstanding(fundamental.get("sharesOutstanding").asLong());
                }
                if (fundamental.has("eps")) {
                    normalizedData.setEps(new BigDecimal(fundamental.get("eps").asText()));
                }
                if (fundamental.has("yield")) {
                    normalizedData.setDividendYield(new BigDecimal(fundamental.get("yield").asText()));
                }
                if (fundamental.has("revenueRaw")) {
                    normalizedData.setRevenue(new BigDecimal(fundamental.get("revenueRaw").asText()));
                }
                if (fundamental.has("oneYearReturn")) {
                    normalizedData.setOneYearReturn(new BigDecimal(fundamental.get("oneYearReturn").asText()));
                }
            }

            log.debug("Extracted fundamental data for {}", normalizedData.getSymbol());
        } catch (Exception e) {
            log.warn("Failed to extract fundamental data: {}", e.getMessage());
        }
    }

    @Override
    public void extractTechnicalData(JsonNode data, NormalizedStockData normalizedData) {
        try {
            JsonNode technical = data.at("/props/pageProps/state/equityStore/instrument/technical/summary");
            if (technical != null && !technical.isMissingNode()) {
                if (technical.has("P1D")) {
                    normalizedData.setTechnicalSummary1d(technical.get("P1D").asText());
                }
                if (technical.has("P1W")) {
                    normalizedData.setTechnicalSummary1w(technical.get("P1W").asText());
                }
                if (technical.has("P1M")) {
                    normalizedData.setTechnicalSummary1m(technical.get("P1M").asText());
                }
            }

            JsonNode performance = data.at("/props/pageProps/state/equityStore/instrument/performance");
            if (performance != null && !performance.isMissingNode() && performance.has("beta")) {
                normalizedData.setBeta(new BigDecimal(performance.get("beta").asText()));
            }

            log.debug("Extracted technical data for {}", normalizedData.getSymbol());
        } catch (Exception e) {
            log.warn("Failed to extract technical data: {}", e.getMessage());
        }
    }

    @Override
    public void extractAnalystData(JsonNode data, NormalizedStockData normalizedData) {
        try {
            JsonNode forecast = data.at("/props/pageProps/state/forecastStore/forecast");
            if (forecast != null && !forecast.isMissingNode()) {
                if (forecast.has("consensus_recommendation")) {
                    normalizedData.setAnalystConsensus(forecast.get("consensus_recommendation").asText());
                }
                if (forecast.has("number_of_analysts_buy")) {
                    normalizedData.setAnalystBuyCount(forecast.get("number_of_analysts_buy").asInt());
                }
                if (forecast.has("number_of_analysts_sell")) {
                    normalizedData.setAnalystSellCount(forecast.get("number_of_analysts_sell").asInt());
                }
                if (forecast.has("number_of_analysts_hold")) {
                    normalizedData.setAnalystHoldCount(forecast.get("number_of_analysts_hold").asInt());
                }
            }

            log.debug("Extracted analyst data for {}", normalizedData.getSymbol());
        } catch (Exception e) {
            log.warn("Failed to extract analyst data: {}", e.getMessage());
        }
    }

    @Override
    public void extractBalanceSheetData(JsonNode data, NormalizedStockData normalizedData) {
        try {
            JsonNode balanceSheet = data.at("/props/pageProps/state/balanceSheetStore/balanceSheetDataAnnual");
            if (balanceSheet != null && !balanceSheet.isMissingNode()) {
                JsonNode reports = balanceSheet.get("reports");
                if (reports != null && reports.isArray() && !reports.isEmpty()) {
                    JsonNode latestReport = reports.get(reports.size() - 1);
                    JsonNode indicators = latestReport.get("indicators");

                    if (indicators != null) {
                        JsonNode totalEquity = indicators.get("total_equity_standard");
                        if (totalEquity == null) {
                            totalEquity = indicators.get("total_equity");
                        }
                        if (totalEquity != null && totalEquity.has("value")) {
                            BigDecimal equity = new BigDecimal(totalEquity.get("value").asText());
                            // Store total equity for BVPS calculation
                            normalizedData.setTotalEquity(equity);
                            log.debug("Extracted Total Equity for {}: {}", normalizedData.getSymbol(), equity);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract balance sheet data: {}", e.getMessage());
        }
    }

    @Override
    public void extractIncomeStatementData(JsonNode data, NormalizedStockData normalizedData) {
        try {
            JsonNode incomeStatement = data.at("/props/pageProps/state/incomeStatementStore/incomeStatementDataAnnual");
            if (incomeStatement != null && !incomeStatement.isMissingNode()) {
                JsonNode reports = incomeStatement.get("reports");
                if (reports != null && reports.isArray() && !reports.isEmpty()) {
                    JsonNode latestReport = reports.get(reports.size() - 1);
                    JsonNode indicators = latestReport.get("indicators");

                    if (indicators != null) {
                        JsonNode netIncome = indicators.get("net_income");
                        if (netIncome != null && netIncome.has("value")) {
                            BigDecimal netIncomeValue = new BigDecimal(netIncome.get("value").asText());
                            normalizedData.setNetIncome(netIncomeValue);
                            log.debug("Extracted Net Income for {}: {}", normalizedData.getSymbol(), netIncomeValue);
                        }

                        JsonNode revenue = indicators.get("total_revenues_standard");
                        if (revenue != null && revenue.has("value")) {
                            BigDecimal revenueValue = new BigDecimal(revenue.get("value").asText());
                            normalizedData.setRevenue(revenueValue);
                            log.debug("Extracted Revenue for {}: {}", normalizedData.getSymbol(), revenueValue);
                        }

                        // Calculate profit margin if both available
                        if (normalizedData.getNetIncome() != null && normalizedData.getRevenue() != null
                                && normalizedData.getRevenue().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal profitMargin = normalizedData.getNetIncome()
                                    .divide(normalizedData.getRevenue(), 4, BigDecimal.ROUND_HALF_UP)
                                    .multiply(new BigDecimal("100"));
                            normalizedData.setProfitMargin(profitMargin);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract income statement data: {}", e.getMessage());
        }
    }

    @Override
    public void extractFinancialRatios(String html, NormalizedStockData normalizedData) {
        try {
            Document doc = Jsoup.parse(html);
            Elements ratioCells = doc.select("div[class~=" + RATIO_CELL_PATTERN.pattern() + "]");

            for (Element cell : ratioCells) {
                Element labelElem = cell.selectFirst("span[class~=" + LABEL_PATTERN.pattern() + "]");
                if (labelElem == null) continue;

                String label = labelElem.text();
                Element valueElem = cell.selectFirst("span[class~=" + VALUE_PATTERN.pattern() + "]");
                if (valueElem == null) continue;

                String value = valueElem.text();

                if (label.contains("P/E Ratio")) {
                    try {
                        normalizedData.setPeRatio(new BigDecimal(value));
                        log.debug("Extracted P/E Ratio for {}: {}", normalizedData.getSymbol(), value);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse P/E Ratio: {}", value);
                    }
                } else if (label.contains("Price/Book")) {
                    try {
                        normalizedData.setPriceToBook(new BigDecimal(value));
                        log.debug("Extracted Price/Book for {}: {}", normalizedData.getSymbol(), value);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse Price/Book: {}", value);
                    }
                } else if (label.contains("Debt / Equity")) {
                    try {
                        String cleanValue = value.replace("%", "").trim();
                        normalizedData.setDebtToEquity(new BigDecimal(cleanValue));
                        log.debug("Extracted Debt/Equity for {}: {}", normalizedData.getSymbol(), cleanValue);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse Debt/Equity: {}", value);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract financial ratios: {}", e.getMessage());
        }
    }
}