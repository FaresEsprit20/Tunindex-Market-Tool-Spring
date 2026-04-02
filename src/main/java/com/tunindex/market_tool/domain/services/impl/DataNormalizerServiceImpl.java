package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.domain.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.domain.entities.Stock;
import com.tunindex.market_tool.domain.entities.embedded.*;
import com.tunindex.market_tool.domain.entities.enums.SectorType;
import com.tunindex.market_tool.domain.services.normalizer.DataNormalizerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class DataNormalizerServiceImpl implements DataNormalizerService {

    @Override
    public NormalizedStockData normalize(NormalizedStockData normalizedData) {
        log.debug("Normalizing stock data for: {}", normalizedData.getSymbol());

        // Clean numeric values
        normalizedData.setLastPrice(cleanNumberValue(normalizedData.getLastPrice()));
        normalizedData.setPrevClose(cleanNumberValue(normalizedData.getPrevClose()));
        normalizedData.setOpen(cleanNumberValue(normalizedData.getOpen()));
        normalizedData.setDayHigh(cleanNumberValue(normalizedData.getDayHigh()));
        normalizedData.setDayLow(cleanNumberValue(normalizedData.getDayLow()));
        normalizedData.setChange(cleanNumberValue(normalizedData.getChange()));
        normalizedData.setChangePct(cleanNumberValue(normalizedData.getChangePct()));

        normalizedData.setVolume(cleanLongValue(normalizedData.getVolume()));
        normalizedData.setAvgVolume3m(cleanLongValue(normalizedData.getAvgVolume3m()));
        normalizedData.setMarketCap(cleanNumberValue(normalizedData.getMarketCap()));
        normalizedData.setSharesOutstanding(cleanLongValue(normalizedData.getSharesOutstanding()));

        normalizedData.setEps(cleanNumberValue(normalizedData.getEps()));
        normalizedData.setPeRatio(cleanNumberValue(normalizedData.getPeRatio()));
        normalizedData.setDividendYield(cleanNumberValue(normalizedData.getDividendYield()));
        normalizedData.setRevenue(cleanNumberValue(normalizedData.getRevenue()));
        normalizedData.setOneYearReturn(cleanNumberValue(normalizedData.getOneYearReturn()));

        normalizedData.setPriceToBook(cleanNumberValue(normalizedData.getPriceToBook()));
        normalizedData.setDebtToEquity(cleanNumberValue(normalizedData.getDebtToEquity()));
        normalizedData.setProfitMargin(cleanNumberValue(normalizedData.getProfitMargin()));

        normalizedData.setBeta(cleanNumberValue(normalizedData.getBeta()));

        normalizedData.setBookValuePerShare(cleanNumberValue(normalizedData.getBookValuePerShare()));
        normalizedData.setGrahamFairValue(cleanNumberValue(normalizedData.getGrahamFairValue()));
        normalizedData.setMarginOfSafety(cleanNumberValue(normalizedData.getMarginOfSafety()));

        return normalizedData;
    }

    @Override
    public Stock toEntity(NormalizedStockData normalizedData) {
        log.debug("Converting normalized data to entity for: {}", normalizedData.getSymbol());

        Stock stock = new Stock();

        // Basic Information
        stock.setSymbol(normalizedData.getSymbol());
        stock.setName(normalizedData.getName());
        stock.setUrl(normalizedData.getUrl());
        stock.setIsin(normalizedData.getIsin());
        stock.setExchange(normalizedData.getExchange());
        stock.setExchangeFullName(normalizedData.getExchangeFullName());
        stock.setMarket(normalizedData.getMarket());
        stock.setCurrency(normalizedData.getCurrency());

        // Convert String sector to SectorType enum
        stock.setSector(convertToSectorType(normalizedData.getSector()));
        stock.setIndustry(normalizedData.getIndustry());

        // Convert String ownership to OwnershipType enum
        if (normalizedData.getOwnershipType() != null) {
            stock.setOwnershipType(normalizedData.getOwnershipType());
        }

        // Price Data
        PriceData priceData = new PriceData();
        priceData.setLastPrice(normalizedData.getLastPrice());
        priceData.setPrevClose(normalizedData.getPrevClose());
        priceData.setOpen(normalizedData.getOpen());
        priceData.setDayHigh(normalizedData.getDayHigh());
        priceData.setDayLow(normalizedData.getDayLow());
        priceData.setChange(normalizedData.getChange());
        priceData.setChangePct(normalizedData.getChangePct());
        priceData.setWeek52High(normalizedData.getWeek52High());
        priceData.setWeek52Low(normalizedData.getWeek52Low());
        priceData.setWeek52Range(normalizedData.getWeek52Range());
        priceData.setCloseTo52weekslowPct(normalizedData.getCloseTo52weekslowPct());
        priceData.setBid(normalizedData.getBid());
        priceData.setAsk(normalizedData.getAsk());
        priceData.setLastUpdateTimestamp(normalizedData.getLastUpdate());
        stock.setPriceData(priceData);

        // Volume Data
        VolumeData volumeData = new VolumeData();
        volumeData.setVolume(normalizedData.getVolume());
        volumeData.setAvgVolume3m(normalizedData.getAvgVolume3m());
        stock.setVolumeData(volumeData);

        // Fundamental Data
        FundamentalData fundamentalData = new FundamentalData();
        fundamentalData.setMarketCap(normalizedData.getMarketCap());
        fundamentalData.setSharesOutstanding(normalizedData.getSharesOutstanding());
        fundamentalData.setEps(normalizedData.getEps());
        fundamentalData.setPeRatio(normalizedData.getPeRatio());
        fundamentalData.setDividendYield(normalizedData.getDividendYield());
        fundamentalData.setRevenue(normalizedData.getRevenue());
        fundamentalData.setOneYearReturn(normalizedData.getOneYearReturn());
        stock.setFundamentalData(fundamentalData);

        // Ratios Data
        RatiosData ratiosData = new RatiosData();
        ratiosData.setPriceToBook(normalizedData.getPriceToBook());
        ratiosData.setDebtToEquity(normalizedData.getDebtToEquity());
        ratiosData.setProfitMargin(normalizedData.getProfitMargin());
        stock.setRatiosData(ratiosData);

        // Technical Data
        TechnicalData technicalData = new TechnicalData();
        technicalData.setBeta(normalizedData.getBeta());
        technicalData.setTechnicalSummary1d(normalizedData.getTechnicalSummary1d());
        technicalData.setTechnicalSummary1w(normalizedData.getTechnicalSummary1w());
        technicalData.setTechnicalSummary1m(normalizedData.getTechnicalSummary1m());
        stock.setTechnicalData(technicalData);

        // Analyst Data
        AnalystData analystData = new AnalystData();
        analystData.setAnalystConsensus(normalizedData.getAnalystConsensus());
        analystData.setAnalystBuyCount(normalizedData.getAnalystBuyCount());
        analystData.setAnalystSellCount(normalizedData.getAnalystSellCount());
        analystData.setAnalystHoldCount(normalizedData.getAnalystHoldCount());
        stock.setAnalystData(analystData);

        // Calculated Values
        CalculatedValues calculatedValues = new CalculatedValues();
        calculatedValues.setGrahamFairValue(normalizedData.getGrahamFairValue());
        calculatedValues.setMarginOfSafety(normalizedData.getMarginOfSafety());
        calculatedValues.setBookValuePerShare(normalizedData.getBookValuePerShare());
        stock.setCalculatedValues(calculatedValues);

        return stock;
    }

    /**
     * Convert String sector to SectorType enum
     */
    private SectorType convertToSectorType(String sector) {
        if (sector == null || sector.isEmpty()) {
            return SectorType.OTHER;
        }

        String sectorLower = sector.toLowerCase();

        if (sectorLower.contains("financial") || sectorLower.contains("bank")) {
            return SectorType.FINANCIALS;
        } else if (sectorLower.contains("banking")) {
            return SectorType.BANKING;
        } else if (sectorLower.contains("technology")) {
            return SectorType.TECHNOLOGY;
        } else if (sectorLower.contains("industrial")) {
            return SectorType.INDUSTRIALS;
        } else if (sectorLower.contains("consumer")) {
            return SectorType.CONSUMER_GOODS;
        } else if (sectorLower.contains("telecom")) {
            return SectorType.TELECOM;
        } else if (sectorLower.contains("energy")) {
            return SectorType.ENERGY;
        } else if (sectorLower.contains("healthcare")) {
            return SectorType.HEALTHCARE;
        } else if (sectorLower.contains("real estate")) {
            return SectorType.REAL_ESTATE;
        } else if (sectorLower.contains("utility")) {
            return SectorType.UTILITIES;
        } else {
            return SectorType.OTHER;
        }
    }

    @Override
    public BigDecimal cleanNumber(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }

        try {
            // Remove spaces and replace comma with dot (European format)
            String cleaned = value.trim()
                    .replace(" ", "")
                    .replace(",", ".");

            // Remove % sign if present
            if (cleaned.endsWith("%")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }

            // Handle 'M' (millions) and 'B' (billions) suffixes
            BigDecimal multiplier = BigDecimal.ONE;
            if (cleaned.endsWith("M") || cleaned.endsWith("m")) {
                multiplier = new BigDecimal("1000000");
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else if (cleaned.endsWith("B") || cleaned.endsWith("b")) {
                multiplier = new BigDecimal("1000000000");
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            } else if (cleaned.endsWith("K") || cleaned.endsWith("k")) {
                multiplier = new BigDecimal("1000");
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }

            BigDecimal number = new BigDecimal(cleaned);
            return number.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        } catch (NumberFormatException e) {
            log.debug("Failed to clean number '{}': {}", value, e.getMessage());
            return null;
        }
    }

    private BigDecimal cleanNumberValue(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private Long cleanLongValue(Long value) {
        if (value == null) {
            return null;
        }
        return value;
    }
}