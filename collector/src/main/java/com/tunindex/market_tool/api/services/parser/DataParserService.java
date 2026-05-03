package com.tunindex.market_tool.api.services.parser;

import com.tunindex.market_tool.api.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.api.dto.providers.investingcom.RawStockData;

public interface DataParserService {
    NormalizedStockData parseToNormalized(RawStockData rawData);
}