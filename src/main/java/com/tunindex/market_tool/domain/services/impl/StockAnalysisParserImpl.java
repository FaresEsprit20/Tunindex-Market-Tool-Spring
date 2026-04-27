package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.core.utils.constants.Constants;
import com.tunindex.market_tool.domain.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;
import com.tunindex.market_tool.domain.services.parser.DataParserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class StockAnalysisParserImpl implements DataParserService {

    @Override
    public NormalizedStockData parseToNormalized(RawStockData rawData) {
        log.debug("Parsing StockAnalysis data for symbol: {}", rawData.getSymbol());

        NormalizedStockData normalizedData = new NormalizedStockData();

        // Set basic info from stockInfo
        Constants.StockInfo stockInfo = rawData.getStockInfo();
        normalizedData.setSymbol(stockInfo.symbol());
        normalizedData.setName(stockInfo.name());
        normalizedData.setUrl(stockInfo.url());
        normalizedData.setOwnershipType(stockInfo.ownershipType());
        normalizedData.setIndustry(stockInfo.industry());
        normalizedData.setCountry(stockInfo.country());

        // Parse HTML data
        String html = rawData.getMainPageHtml();
        if (html != null && !html.isEmpty()) {
            Document doc = Jsoup.parse(html);

            // Extract all fields
            extractMarketCap(doc, normalizedData);
            extractPriceData(doc, normalizedData);
            extractRevenue(doc, normalizedData);
            extractRatios(doc, normalizedData);
            extractFinancialHealth(doc, normalizedData);
            extractProfitability(doc, normalizedData);
            extractDividends(doc, normalizedData);
        }

        // Post-process calculations
        calculateDerivedFields(normalizedData);

        return normalizedData;
    }

    private void extractMarketCap(Document doc, NormalizedStockData normalizedData) {
        Element elem = doc.selectFirst(".market-cap");
        if (elem != null) {
            String value = elem.text().replace("Market Cap: ", "");
            normalizedData.setMarketCap(parseNumberWithSuffix(value));
        }
    }

    private void extractPriceData(Document doc, NormalizedStockData normalizedData) {
        Element priceElem = doc.selectFirst(".price");
        if (priceElem != null) {
            String value = priceElem.text().replace("Price: ", "");
            setBigDecimal(value, normalizedData::setLastPrice);
        }

        Element changeElem = doc.selectFirst(".change");
        if (changeElem != null) {
            String value = changeElem.text().replace("Change: ", "").replace("%", "");
            setBigDecimal(value, normalizedData::setChangePct);
        }
    }

    private void extractRevenue(Document doc, NormalizedStockData normalizedData) {
        Element elem = doc.selectFirst(".revenue");
        if (elem != null) {
            String value = elem.text().replace("Revenue: ", "");
            normalizedData.setRevenue(parseNumberWithSuffix(value));
        }
    }

    private void extractRatios(Document doc, NormalizedStockData normalizedData) {
        Element peElem = doc.selectFirst(".pe-ratio");
        if (peElem != null) {
            String value = peElem.text().replace("P/E Ratio: ", "");
            setBigDecimal(value, normalizedData::setPeRatio);
        }

        Element pbElem = doc.selectFirst(".pb-ratio");
        if (pbElem != null) {
            String value = pbElem.text().replace("P/B Ratio: ", "");
            setBigDecimal(value, normalizedData::setPriceToBook);
        }
    }

    private void extractFinancialHealth(Document doc, NormalizedStockData normalizedData) {
        Element debtEquityElem = doc.selectFirst(".debt-equity");
        if (debtEquityElem != null) {
            String value = debtEquityElem.text().replace("Debt/Equity: ", "");
            setBigDecimal(value, normalizedData::setDebtToEquity);
        }

        Element bvpsElem = doc.selectFirst(".book-value-per-share");
        if (bvpsElem != null) {
            String value = bvpsElem.text().replace("Book Value Per Share: ", "");
            setBigDecimal(value, normalizedData::setBookValuePerShare);
        }
    }

    private void extractProfitability(Document doc, NormalizedStockData normalizedData) {
        Element profitMarginElem = doc.selectFirst(".profit-margin");
        if (profitMarginElem != null) {
            String value = profitMarginElem.text().replace("Profit Margin: ", "").replace("%", "");
            setBigDecimal(value, normalizedData::setProfitMargin);
        }
    }

    private void extractDividends(Document doc, NormalizedStockData normalizedData) {
        Element divYieldElem = doc.selectFirst(".dividend-yield");
        if (divYieldElem != null) {
            String value = divYieldElem.text().replace("Dividend Yield: ", "").replace("%", "");
            setBigDecimal(value, normalizedData::setDividendYield);
        }
    }

    private void calculateDerivedFields(NormalizedStockData normalizedData) {
        // Calculate 52-week range
        if (normalizedData.getWeek52Low() != null && normalizedData.getWeek52High() != null) {
            normalizedData.setWeek52Range(normalizedData.getWeek52Low() + " - " + normalizedData.getWeek52High());
        }

        // Calculate profit margin if not set
        if (normalizedData.getProfitMargin() == null &&
                normalizedData.getNetIncome() != null &&
                normalizedData.getRevenue() != null &&
                normalizedData.getRevenue().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitMargin = normalizedData.getNetIncome()
                    .divide(normalizedData.getRevenue(), 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));
            normalizedData.setProfitMargin(profitMargin);
        }
    }

    private BigDecimal parseNumberWithSuffix(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;

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

            return BigDecimal.valueOf(Double.parseDouble(value) * multiplier);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse number: {}", value);
            return BigDecimal.ZERO;
        }
    }

    private void setBigDecimal(String value, java.util.function.Consumer<BigDecimal> setter) {
        try {
            setter.accept(new BigDecimal(value));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse number: {}", value);
        }
    }
}