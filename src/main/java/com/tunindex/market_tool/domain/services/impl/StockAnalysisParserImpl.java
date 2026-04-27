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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

@Service
@Slf4j
public class StockAnalysisParserImpl implements DataParserService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d, yyyy");

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

        // Set source
        normalizedData.setSource(Constants.PROVIDER_STOCKANALYSIS);

        // Set last update timestamp
        normalizedData.setLastUpdate(System.currentTimeMillis());

        // Parse HTML data
        String html = rawData.getMainPageHtml();
        if (html != null && !html.isEmpty()) {
            Document doc = Jsoup.parse(html);

            // Extract ALL fields
            extractBasicInfo(doc, normalizedData);
            extractMarketCap(doc, normalizedData);
            extractPriceData(doc, normalizedData);      // ← THIS NEEDS TO BE FIXED
            extractVolumeData(doc, normalizedData);
            extractRevenueAndEarnings(doc, normalizedData);
            extractRatios(doc, normalizedData);        // ← ADDED PB RATIO
            extractDividends(doc, normalizedData);
            extract52WeekRange(doc, normalizedData);
            extractTechnicalData(doc, normalizedData);
            extractExchangeInfo(doc, normalizedData);
            extractDebtToEquity(doc, normalizedData);   // ← NEW
            extractProfitMargin(doc, normalizedData);   // ← NEW
            extractBookValuePerShare(doc, normalizedData); // ← NEW
        }

        // Post-process calculations
        calculateDerivedFields(normalizedData);

        // Log extracted data for debugging
        log.info("📝 Parsed data for {}: price={}, change={}, changePct={}, open={}, prevClose={}, dayHigh={}, dayLow={}, volume={}",
                normalizedData.getSymbol(),
                normalizedData.getLastPrice(),
                normalizedData.getChange(),
                normalizedData.getChangePct(),
                normalizedData.getOpen(),
                normalizedData.getPrevClose(),
                normalizedData.getDayHigh(),
                normalizedData.getDayLow(),
                normalizedData.getVolume());

        return normalizedData;
    }

    /**
     * Extract basic info including exchange, market, currency
     */
    private void extractBasicInfo(Document doc, NormalizedStockData normalizedData) {
        Element countryElem = doc.selectFirst(".country");
        if (countryElem != null && normalizedData.getCountry() == null) {
            String countryText = cleanLabel(countryElem.text(), "Country");
            normalizedData.setCountry(countryText);
        }

        if (normalizedData.getCountry() != null) {
            normalizedData.setMarket(normalizedData.getCountry());
        } else {
            normalizedData.setMarket("Tunisia");
        }

        if ("Tunisia".equals(normalizedData.getCountry()) ||
                "Tunis Stock Exchange".equals(normalizedData.getExchange())) {
            normalizedData.setCurrency("TND");
        } else {
            normalizedData.setCurrency("TND");
        }

        if (normalizedData.getIndustry() != null) {
            String industry = normalizedData.getIndustry().toLowerCase();
            if (industry.contains("bank") || industry.contains("financial")) {
                normalizedData.setSector("Financials");
            } else if (industry.contains("tech") || industry.contains("software")) {
                normalizedData.setSector("Technology");
            } else if (industry.contains("industrial") || industry.contains("manufacturing")) {
                normalizedData.setSector("Industrials");
            } else {
                normalizedData.setSector("Other");
            }
        } else {
            normalizedData.setSector("Other");
        }
    }

    /**
     * Extract exchange information
     */
    private void extractExchangeInfo(Document doc, NormalizedStockData normalizedData) {
        Element exchangeElem = doc.selectFirst(".exchange");
        if (exchangeElem != null) {
            String exchangeText = cleanLabel(exchangeElem.text(), "Exchange");
            if (exchangeText != null && !exchangeText.isEmpty()) {
                normalizedData.setExchange(exchangeText);
                log.debug("Extracted exchange: {}", exchangeText);
            }
        }

        if (normalizedData.getExchange() == null || normalizedData.getExchange().isEmpty()) {
            normalizedData.setExchange("Tunis Stock Exchange");
            log.warn("Exchange not found, using default");
        }

        Element exchangeCodeElem = doc.selectFirst(".exchange-code");
        if (exchangeCodeElem != null) {
            String exchangeCode = cleanLabel(exchangeCodeElem.text(), "Exchange Code");
            if (exchangeCode != null && !exchangeCode.isEmpty()) {
                normalizedData.setExchangeFullName(exchangeCode);
                log.debug("Extracted exchange code: {}", exchangeCode);
            }
        }

        if (normalizedData.getExchangeFullName() == null || normalizedData.getExchangeFullName().isEmpty()) {
            normalizedData.setExchangeFullName("BVMT");
            log.warn("Exchange code not found, using default");
        }
    }

    private void extractMarketCap(Document doc, NormalizedStockData normalizedData) {
        Element elem = doc.selectFirst(".market-cap");
        if (elem != null) {
            String value = cleanLabel(elem.text(), "Market Cap");
            normalizedData.setMarketCap(parseNumberWithSuffix(value));
        }
    }

    /**
     * FIXED: Extract ALL price data including price, change, open, close, high, low
     */
    private void extractPriceData(Document doc, NormalizedStockData normalizedData) {
        // Current price - from .price class
        Element priceElem = doc.selectFirst(".price");
        if (priceElem != null) {
            String value = cleanLabel(priceElem.text(), "Price");
            setBigDecimal(value, normalizedData::setLastPrice);
            log.info("💰 Extracted price: {}", value);
        }

        // Change - from .change class (format: "+1.01 (0.70%)")
        Element changeElem = doc.selectFirst(".change");
        if (changeElem != null) {
            String value = cleanLabel(changeElem.text(), "Change %");
            if (value != null && !value.isEmpty()) {
                // Extract percentage from "(0.70%)"
                java.util.regex.Pattern percentPattern = java.util.regex.Pattern.compile("\\(([0-9.]+)%\\)");
                java.util.regex.Matcher percentMatcher = percentPattern.matcher(value);
                if (percentMatcher.find()) {
                    setBigDecimal(percentMatcher.group(1), normalizedData::setChangePct);
                    log.info("📈 Extracted change percent: {}%", percentMatcher.group(1));
                }

                // Extract absolute change from "+1.01"
                java.util.regex.Pattern amountPattern = java.util.regex.Pattern.compile("([+-][0-9.]+)\\s*\\(");
                java.util.regex.Matcher amountMatcher = amountPattern.matcher(value);
                if (amountMatcher.find()) {
                    setBigDecimal(amountMatcher.group(1), normalizedData::setChange);
                    log.info("📈 Extracted change amount: {}", amountMatcher.group(1));
                }
            }
        }

        // Open price
        Element openElem = doc.selectFirst(".open");
        if (openElem != null) {
            String value = cleanLabel(openElem.text(), "Open");
            setBigDecimal(value, normalizedData::setOpen);
            log.info("🎯 Extracted open: {}", value);
        }

        // Previous Close
        Element prevCloseElem = doc.selectFirst(".prev-close");
        if (prevCloseElem != null) {
            String value = cleanLabel(prevCloseElem.text(), "Previous Close");
            setBigDecimal(value, normalizedData::setPrevClose);
            log.info("🔚 Extracted previous close: {}", value);
        }

        // Day High
        Element dayHighElem = doc.selectFirst(".day-high");
        if (dayHighElem != null) {
            String value = cleanLabel(dayHighElem.text(), "Day High");
            setBigDecimal(value, normalizedData::setDayHigh);
            log.info("📈 Extracted day high: {}", value);
        }

        // Day Low
        Element dayLowElem = doc.selectFirst(".day-low");
        if (dayLowElem != null) {
            String value = cleanLabel(dayLowElem.text(), "Day Low");
            setBigDecimal(value, normalizedData::setDayLow);
            log.info("📉 Extracted day low: {}", value);
        }
    }

    private void extractVolumeData(Document doc, NormalizedStockData normalizedData) {
        Element volumeElem = doc.selectFirst(".volume");
        if (volumeElem != null) {
            String value = cleanLabel(volumeElem.text(), "Volume");
            if (value != null && !value.isEmpty()) {
                try {
                    String cleanValue = value.replace(",", "");
                    normalizedData.setVolume(Long.parseLong(cleanValue));
                    log.info("📊 Extracted volume: {}", cleanValue);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse volume: {}", value);
                }
            }
        }

        Element avgVolumeElem = doc.selectFirst(".avg-volume");
        if (avgVolumeElem != null) {
            String value = cleanLabel(avgVolumeElem.text(), "Average Volume");
            if (value != null && !value.isEmpty()) {
                try {
                    String cleanValue = value.replace(",", "");
                    normalizedData.setAvgVolume3m(Long.parseLong(cleanValue));
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse avg volume: {}", value);
                }
            }
        }
    }

    private void extractRevenueAndEarnings(Document doc, NormalizedStockData normalizedData) {
        Element revenueElem = doc.selectFirst(".revenue");
        if (revenueElem != null) {
            String value = cleanLabel(revenueElem.text(), "Revenue");
            normalizedData.setRevenue(parseNumberWithSuffix(value));
        }

        Element netIncomeElem = doc.selectFirst(".net-income");
        if (netIncomeElem != null) {
            String value = cleanLabel(netIncomeElem.text(), "Net Income");
            normalizedData.setNetIncome(parseNumberWithSuffix(value));
        }

        Element epsElem = doc.selectFirst(".eps");
        if (epsElem != null) {
            String value = cleanLabel(epsElem.text(), "EPS");
            setBigDecimal(value, normalizedData::setEps);
        }

        Element sharesOutElem = doc.selectFirst(".shares-outstanding");
        if (sharesOutElem != null) {
            String value = cleanLabel(sharesOutElem.text(), "Shares Outstanding");
            if (value != null && !value.isEmpty()) {
                try {
                    if (value.endsWith("M")) {
                        value = value.substring(0, value.length() - 1);
                        BigDecimal shares = new BigDecimal(value);
                        normalizedData.setSharesOutstanding(shares.multiply(new BigDecimal("1000000")).longValue());
                    } else {
                        normalizedData.setSharesOutstanding(Long.parseLong(value.replace(",", "")));
                    }
                    log.info("📋 Extracted shares outstanding: {}", normalizedData.getSharesOutstanding());
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse shares outstanding: {}", value);
                }
            }
        }
    }

    private void extractRatios(Document doc, NormalizedStockData normalizedData) {
        Element peElem = doc.selectFirst(".pe-ratio");
        if (peElem != null) {
            String value = cleanLabel(peElem.text(), "P/E Ratio");
            setBigDecimal(value, normalizedData::setPeRatio);
        }

        // Extract PB Ratio (Price to Book)
        Element pbElem = doc.selectFirst(".pb-ratio");
        if (pbElem != null) {
            String value = cleanLabel(pbElem.text(), "P/B Ratio");
            setBigDecimal(value, normalizedData::setPriceToBook);
            log.info("📐 Extracted P/B Ratio: {}", value);
        }
    }

    private void extractDividends(Document doc, NormalizedStockData normalizedData) {
        Element divYieldElem = doc.selectFirst(".dividend-yield");
        if (divYieldElem != null) {
            String value = cleanLabel(divYieldElem.text(), "Dividend Yield %");
            if (value != null) {
                String cleanValue = value.replace("%", "");
                setBigDecimal(cleanValue, normalizedData::setDividendYield);
                log.info("💸 Extracted dividend yield: {}%", cleanValue);
            }
        }

        Element payoutElem = doc.selectFirst(".payout-ratio");
        if (payoutElem != null) {
            String value = cleanLabel(payoutElem.text(), "Payout Ratio %");
            if (value != null) {
                String cleanValue = value.replace("%", "");
                setBigDecimal(cleanValue, normalizedData::setPayoutRatio);
            }
        }
    }

    private void extract52WeekRange(Document doc, NormalizedStockData normalizedData) {
        Element lowElem = doc.selectFirst(".week52-low");
        if (lowElem != null) {
            String value = cleanLabel(lowElem.text(), "52-Week Low");
            setBigDecimal(value, normalizedData::setWeek52Low);
            log.info("📉 Extracted 52-week low: {}", value);
        }

        Element highElem = doc.selectFirst(".week52-high");
        if (highElem != null) {
            String value = cleanLabel(highElem.text(), "52-Week High");
            setBigDecimal(value, normalizedData::setWeek52High);
            log.info("📈 Extracted 52-week high: {}", value);
        }

        Element rangeElem = doc.selectFirst(".week52-range");
        if (rangeElem != null) {
            String value = cleanLabel(rangeElem.text(), "52-Week Range");
            normalizedData.setWeek52Range(value);
        }

        if (normalizedData.getWeek52Low() != null && normalizedData.getWeek52High() != null) {
            normalizedData.setWeek52Range(normalizedData.getWeek52Low() + " - " + normalizedData.getWeek52High());
        }
    }

    private void extractTechnicalData(Document doc, NormalizedStockData normalizedData) {
        Element betaElem = doc.selectFirst(".beta");
        if (betaElem != null) {
            String value = cleanLabel(betaElem.text(), "Beta");
            setBigDecimal(value, normalizedData::setBeta);
        }
    }

    /**
     * NEW: Extract Debt/Equity ratio
     */
    private void extractDebtToEquity(Document doc, NormalizedStockData normalizedData) {
        Element debtEquityElem = doc.selectFirst(".debt-equity");
        if (debtEquityElem != null) {
            String value = cleanLabel(debtEquityElem.text(), "Debt/Equity");
            setBigDecimal(value, normalizedData::setDebtToEquity);
            log.info("💼 Extracted Debt/Equity: {}", value);
        }
    }

    /**
     * NEW: Extract Profit Margin
     */
    private void extractProfitMargin(Document doc, NormalizedStockData normalizedData) {
        Element profitMarginElem = doc.selectFirst(".profit-margin");
        if (profitMarginElem != null) {
            String value = cleanLabel(profitMarginElem.text(), "Profit Margin %");
            if (value != null) {
                String cleanValue = value.replace("%", "");
                setBigDecimal(cleanValue, normalizedData::setProfitMargin);
                log.info("📊 Extracted Profit Margin: {}%", cleanValue);
            }
        }
    }

    /**
     * NEW: Extract Book Value Per Share
     */
    private void extractBookValuePerShare(Document doc, NormalizedStockData normalizedData) {
        Element bvpsElem = doc.selectFirst(".book-value-per-share");
        if (bvpsElem != null) {
            String value = cleanLabel(bvpsElem.text(), "Book Value Per Share");
            setBigDecimal(value, normalizedData::setBookValuePerShare);
            log.info("📚 Extracted Book Value Per Share: {}", value);
        }
    }

    private void calculateDerivedFields(NormalizedStockData normalizedData) {
        // Calculate profit margin if not set
        if (normalizedData.getProfitMargin() == null &&
                normalizedData.getNetIncome() != null &&
                normalizedData.getRevenue() != null &&
                normalizedData.getRevenue().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitMargin = normalizedData.getNetIncome()
                    .divide(normalizedData.getRevenue(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            normalizedData.setProfitMargin(profitMargin);
        }
    }

    private String cleanLabel(String text, String label) {
        if (text == null) return null;
        String cleaned = text;
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(cleaned.indexOf(":") + 1).trim();
        }
        if (cleaned.startsWith(label)) {
            cleaned = cleaned.substring(label.length()).trim();
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private BigDecimal parseNumberWithSuffix(String value) {
        if (value == null || value.isEmpty()) return null;

        try {
            value = value.trim().toUpperCase();
            double multiplier = 1.0;

            boolean isNegative = value.startsWith("-");
            if (isNegative) {
                value = value.substring(1);
            }

            if (value.endsWith("B")) {
                multiplier = 1_000_000_000.0;
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("M")) {
                multiplier = 1_000_000.0;
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("K")) {
                multiplier = 1_000.0;
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("%")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 1.0;
            }

            value = value.replace(",", "");

            BigDecimal result = BigDecimal.valueOf(Double.parseDouble(value) * multiplier);
            if (isNegative) {
                result = result.negate();
            }
            return result;
        } catch (NumberFormatException e) {
            log.warn("Failed to parse number with suffix: {}", value);
            return null;
        }
    }

    private void setBigDecimal(String value, Consumer<BigDecimal> setter) {
        if (value == null || value.isEmpty()) return;
        try {
            String cleanValue = value.trim();
            if (cleanValue.endsWith("%")) {
                cleanValue = cleanValue.substring(0, cleanValue.length() - 1);
            }
            cleanValue = cleanValue.replace(",", "");
            setter.accept(new BigDecimal(cleanValue));
        } catch (NumberFormatException e) {
            log.debug("Failed to parse number: {}", value);
        }
    }
}