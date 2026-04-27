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
            extractPriceData(doc, normalizedData);
            extractVolumeData(doc, normalizedData);
            extractRevenueAndEarnings(doc, normalizedData);
            extractRatios(doc, normalizedData);
            extractDividends(doc, normalizedData);
            extract52WeekRange(doc, normalizedData);
            extractTechnicalData(doc, normalizedData);
            extractExchangeInfo(doc, normalizedData);
        }

        // Post-process calculations
        calculateDerivedFields(normalizedData);

        // Log extracted data for debugging
        log.info("📝 Parsed data for {}: exchange='{}', exchangeFullName='{}', market='{}', currency='{}', price={}",
                normalizedData.getSymbol(),
                normalizedData.getExchange(),
                normalizedData.getExchangeFullName(),
                normalizedData.getMarket(),
                normalizedData.getCurrency(),
                normalizedData.getLastPrice());

        return normalizedData;
    }

    /**
     * Extract basic info including exchange, market, currency
     */
    private void extractBasicInfo(Document doc, NormalizedStockData normalizedData) {
        // Extract country (already set from stockInfo, but can override from HTML)
        Element countryElem = doc.selectFirst(".country");
        if (countryElem != null && normalizedData.getCountry() == null) {
            String countryText = cleanLabel(countryElem.text(), "Country");
            normalizedData.setCountry(countryText);
        }

        // Set market from country
        if (normalizedData.getCountry() != null) {
            normalizedData.setMarket(normalizedData.getCountry());
        } else {
            normalizedData.setMarket("Tunisia");
        }

        // Set currency (TND for Tunisia)
        if ("Tunisia".equals(normalizedData.getCountry()) ||
                "Tunis Stock Exchange".equals(normalizedData.getExchange())) {
            normalizedData.setCurrency("TND");
        } else {
            normalizedData.setCurrency("TND"); // Default for BVMT
        }

        // Extract sector from industry or set default
        if (normalizedData.getIndustry() != null) {
            // Determine sector based on industry
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
     * Extract exchange information - CRITICAL for database save
     */
    private void extractExchangeInfo(Document doc, NormalizedStockData normalizedData) {
        // Extract exchange name from .exchange element
        Element exchangeElem = doc.selectFirst(".exchange");
        if (exchangeElem != null) {
            String exchangeText = cleanLabel(exchangeElem.text(), "Exchange");
            if (exchangeText != null && !exchangeText.isEmpty()) {
                normalizedData.setExchange(exchangeText);
                log.debug("Extracted exchange: {}", exchangeText);
            }
        }

        // If exchange not found, set default
        if (normalizedData.getExchange() == null || normalizedData.getExchange().isEmpty()) {
            normalizedData.setExchange("Tunis Stock Exchange");
            log.warn("Exchange not found in HTML, using default: Tunis Stock Exchange");
        }

        // Extract exchange code from .exchange-code element
        Element exchangeCodeElem = doc.selectFirst(".exchange-code");
        if (exchangeCodeElem != null) {
            String exchangeCode = cleanLabel(exchangeCodeElem.text(), "Exchange Code");
            if (exchangeCode != null && !exchangeCode.isEmpty()) {
                normalizedData.setExchangeFullName(exchangeCode);
                log.debug("Extracted exchange code: {}", exchangeCode);
            }
        }

        // If exchange code not found, try to derive from symbol or use default
        if (normalizedData.getExchangeFullName() == null || normalizedData.getExchangeFullName().isEmpty()) {
            // Try to extract from exchange name
            if (normalizedData.getExchange() != null) {
                if (normalizedData.getExchange().contains("Tunis")) {
                    normalizedData.setExchangeFullName("BVMT");
                } else {
                    normalizedData.setExchangeFullName(normalizedData.getExchange().substring(0, Math.min(4, normalizedData.getExchange().length())));
                }
            } else {
                normalizedData.setExchangeFullName("BVMT");
            }
            log.warn("Exchange code not found, using derived: {}", normalizedData.getExchangeFullName());
        }
    }

    private void extractMarketCap(Document doc, NormalizedStockData normalizedData) {
        Element elem = doc.selectFirst(".market-cap");
        if (elem != null) {
            String value = cleanLabel(elem.text(), "Market Cap");
            normalizedData.setMarketCap(parseNumberWithSuffix(value));
        }
    }

    private void extractPriceData(Document doc, NormalizedStockData normalizedData) {
        // Current price
        Element priceElem = doc.selectFirst(".price");
        if (priceElem != null) {
            String value = cleanLabel(priceElem.text(), "Price");
            setBigDecimal(value, normalizedData::setLastPrice);
        }

        // Change percentage
        Element changeElem = doc.selectFirst(".change");
        if (changeElem != null) {
            String value = cleanLabel(changeElem.text(), "Change %");
            if (value != null && !value.isEmpty()) {
                String cleanValue = value.replace("%", "");
                setBigDecimal(cleanValue, normalizedData::setChangePct);
                // Also set the change amount separately if available
                if (value.contains("(") && value.contains(")")) {
                    // Pattern like "+1.01 (0.70%)" - extract the number before parenthesis
                    String[] parts = value.split("\\(");
                    if (parts.length > 0) {
                        setBigDecimal(parts[0].trim(), normalizedData::setChange);
                    }
                }
            }
        }

        // Open
        Element openElem = doc.selectFirst(".open");
        if (openElem != null) {
            String value = cleanLabel(openElem.text(), "Open");
            setBigDecimal(value, normalizedData::setOpen);
        }

        // Previous Close
        Element prevCloseElem = doc.selectFirst(".prev-close");
        if (prevCloseElem != null) {
            String value = cleanLabel(prevCloseElem.text(), "Previous Close");
            setBigDecimal(value, normalizedData::setPrevClose);
        }

        // Day High
        Element dayHighElem = doc.selectFirst(".day-high");
        if (dayHighElem != null) {
            String value = cleanLabel(dayHighElem.text(), "Day High");
            setBigDecimal(value, normalizedData::setDayHigh);
        }

        // Day Low
        Element dayLowElem = doc.selectFirst(".day-low");
        if (dayLowElem != null) {
            String value = cleanLabel(dayLowElem.text(), "Day Low");
            setBigDecimal(value, normalizedData::setDayLow);
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
                    String cleanValue = value.replace(",", "");
                    BigDecimal shares = new BigDecimal(cleanValue);
                    if (value.contains("M") || value.contains("m")) {
                        shares = shares.multiply(new BigDecimal("1000000"));
                    } else if (value.contains("B") || value.contains("b")) {
                        shares = shares.multiply(new BigDecimal("1000000000"));
                    }
                    normalizedData.setSharesOutstanding(shares.longValue());
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

        Element forwardPEElem = doc.selectFirst(".forward-pe");
        if (forwardPEElem != null) {
            String value = cleanLabel(forwardPEElem.text(), "Forward P/E");
            // Could store in a separate field if needed
        }
    }

    private void extractDividends(Document doc, NormalizedStockData normalizedData) {
        Element divElem = doc.selectFirst(".dividend");
        if (divElem != null) {
            String value = cleanLabel(divElem.text(), "Dividend");
            // Dividend might be like "6.00 (4.14%)"
            if (value != null && value.contains("(")) {
                String[] parts = value.split("\\(");
                if (parts.length > 0) {
                    // Extract the percentage
                    String yieldPart = parts[1].replace(")", "").replace("%", "");
                    setBigDecimal(yieldPart, normalizedData::setDividendYield);
                }
            }
        }

        Element divYieldElem = doc.selectFirst(".dividend-yield");
        if (divYieldElem != null) {
            String value = cleanLabel(divYieldElem.text(), "Dividend Yield %");
            if (value != null) {
                String cleanValue = value.replace("%", "");
                setBigDecimal(cleanValue, normalizedData::setDividendYield);
            }
        }

    }

    private void extract52WeekRange(Document doc, NormalizedStockData normalizedData) {
        Element rangeElem = doc.selectFirst(".week52-range");
        if (rangeElem != null) {
            String value = cleanLabel(rangeElem.text(), "52-Week Range");
            normalizedData.setWeek52Range(value);

            // Parse individual values
            if (value != null && value.contains("-")) {
                String[] parts = value.split("-");
                if (parts.length == 2) {
                    setBigDecimal(parts[0].trim(), normalizedData::setWeek52Low);
                    setBigDecimal(parts[1].trim(), normalizedData::setWeek52High);
                }
            }
        } else {
            // Try individual elements
            Element lowElem = doc.selectFirst(".week52-low");
            if (lowElem != null) {
                String value = cleanLabel(lowElem.text(), "52-Week Low");
                setBigDecimal(value, normalizedData::setWeek52Low);
            }

            Element highElem = doc.selectFirst(".week52-high");
            if (highElem != null) {
                String value = cleanLabel(highElem.text(), "52-Week High");
                setBigDecimal(value, normalizedData::setWeek52High);
            }
        }
    }

    private void extractTechnicalData(Document doc, NormalizedStockData normalizedData) {
        Element betaElem = doc.selectFirst(".beta");
        if (betaElem != null) {
            String value = cleanLabel(betaElem.text(), "Beta");
            setBigDecimal(value, normalizedData::setBeta);
        }

        Element rsiElem = doc.selectFirst(".rsi");
        if (rsiElem != null) {
            String value = cleanLabel(rsiElem.text(), "RSI");
            // RSI can be stored if there's a field, or logged
            log.debug("RSI for {}: {}", normalizedData.getSymbol(), value);
        }
    }



    private void calculateDerivedFields(NormalizedStockData normalizedData) {
        // Calculate 52-week range string if not already set
        if (normalizedData.getWeek52Range() == null &&
                normalizedData.getWeek52Low() != null &&
                normalizedData.getWeek52High() != null) {
            normalizedData.setWeek52Range(normalizedData.getWeek52Low() + " - " + normalizedData.getWeek52High());
        }

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

        // Calculate close to 52-week low percentage
        if (normalizedData.getCloseTo52weekslowPct() == null &&
                normalizedData.getLastPrice() != null &&
                normalizedData.getWeek52Low() != null &&
                normalizedData.getWeek52Low().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = normalizedData.getLastPrice()
                    .subtract(normalizedData.getWeek52Low())
                    .divide(normalizedData.getWeek52Low(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            normalizedData.setCloseTo52weekslowPct(pct);
        }
    }

    private String cleanLabel(String text, String label) {
        if (text == null) return null;
        String cleaned = text;
        if (cleaned.contains(":")) {
            cleaned = cleaned.substring(cleaned.indexOf(":") + 1).trim();
        }
        // Remove the label if it's at the beginning
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

            // Remove any negative sign temporarily
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

            // Remove commas
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
            // Remove % if present
            if (cleanValue.endsWith("%")) {
                cleanValue = cleanValue.substring(0, cleanValue.length() - 1);
            }
            // Remove commas
            cleanValue = cleanValue.replace(",", "");
            setter.accept(new BigDecimal(cleanValue));
        } catch (NumberFormatException e) {
            log.debug("Failed to parse number: {}", value);
        }
    }
}