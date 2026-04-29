package com.tunindex.market_tool.collector.services.parser;

import com.tunindex.market_tool.common.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.common.dto.providers.investingcom.RawStockData;

public interface DataParserService {
    NormalizedStockData parseToNormalized(RawStockData rawData);
}