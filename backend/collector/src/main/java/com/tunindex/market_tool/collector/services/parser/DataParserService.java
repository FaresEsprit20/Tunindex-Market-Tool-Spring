package com.tunindex.market_tool.collector.services.parser;

import com.tunindex.market_tool.collector.dto.investingcom.NormalizedStockData;
import com.tunindex.market_tool.collector.dto.investingcom.RawStockData;

public interface DataParserService {
    NormalizedStockData parseToNormalized(RawStockData rawData);
}