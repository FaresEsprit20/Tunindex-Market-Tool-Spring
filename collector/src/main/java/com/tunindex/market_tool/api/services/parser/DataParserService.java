package com.tunindex.market_tool.api.services.parser;

import com.tunindex.market_tool.api.dto.investingcom.NormalizedStockData;
import com.tunindex.market_tool.api.dto.investingcom.RawStockData;

public interface DataParserService {
    NormalizedStockData parseToNormalized(RawStockData rawData);
}