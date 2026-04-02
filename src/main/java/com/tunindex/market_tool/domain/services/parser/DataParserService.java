package com.tunindex.market_tool.domain.services.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.tunindex.market_tool.domain.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;


public interface DataParserService {

    /**
     * Parse raw HTML data to normalized stock data
     * Maps to Python: Main parsing logic from investingdotcom.py
     */
    NormalizedStockData parseToNormalized(RawStockData rawData);

    /**
     * Parse __NEXT_DATA__ script from HTML
     */
    JsonNode parseNextDataScript(String html, String symbol);

    /**
     * Extract stock basic info from JSON
     */
    void extractBasicInfo(JsonNode data, NormalizedStockData normalizedData);

    /**
     * Extract price data from JSON
     */
    void extractPriceData(JsonNode data, NormalizedStockData normalizedData);

    /**
     * Extract fundamental data from JSON
     */
    void extractFundamentalData(JsonNode data, NormalizedStockData normalizedData);

    /**
     * Extract technical data from JSON
     */
    void extractTechnicalData(JsonNode data, NormalizedStockData normalizedData);

    /**
     * Extract analyst data from JSON
     */
    void extractAnalystData(JsonNode data, NormalizedStockData normalizedData);

    /**
     * Extract balance sheet data from JSON
     */
    void extractBalanceSheetData(JsonNode data, NormalizedStockData normalizedData);

    /**
     * Extract income statement data from JSON
     */
    void extractIncomeStatementData(JsonNode data, NormalizedStockData normalizedData);

    /**
     * Extract financial ratios from HTML
     */
    void extractFinancialRatios(String html, NormalizedStockData normalizedData);
}