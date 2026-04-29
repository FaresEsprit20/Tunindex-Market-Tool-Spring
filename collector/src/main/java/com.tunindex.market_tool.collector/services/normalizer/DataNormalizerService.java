package com.tunindex.market_tool.collector.services.normalizer;


import com.tunindex.market_tool.common.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.common.entities.Stock;

import java.math.BigDecimal;

public interface DataNormalizerService {

    /**
     * Normalize raw data to normalized stock data
     * Maps to Python: services/normalizer.py - normalize_stock(raw)
     */
    NormalizedStockData normalize(NormalizedStockData normalizedData);

    /**
     * Convert normalized data to Stock entity
     */
    Stock toEntity(NormalizedStockData normalizedData);

    /**
     * Clean number by removing spaces and commas, converting to BigDecimal
     * Maps to Python: services/normalizer.py - clean_number(val)
     */
    BigDecimal cleanNumber(String value);
}