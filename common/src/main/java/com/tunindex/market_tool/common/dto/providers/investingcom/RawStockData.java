package com.tunindex.market_tool.common.dto.providers.investingcom;

import com.tunindex.market_tool.common.utils.constants.Constants;
import lombok.Data;

@Data
public class RawStockData {

    private String symbol;
    private Constants.StockInfo stockInfo;
    private String mainPageHtml;
    private String balanceSheetHtml;
    private String incomeStatementHtml;
    private String financialSummaryHtml;

}