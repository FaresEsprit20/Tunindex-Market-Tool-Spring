package com.tunindex.market_tool.domain.services.parser;

import com.tunindex.market_tool.domain.dto.providers.investingcom.NormalizedStockData;
import com.tunindex.market_tool.domain.dto.providers.investingcom.RawStockData;

public interface DataParserService {
    NormalizedStockData parseToNormalized(RawStockData rawData);
}