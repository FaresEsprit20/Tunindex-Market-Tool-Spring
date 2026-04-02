package com.tunindex.market_tool.domain.dto.providers.investingcom;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class NormalizedStockData {
    // Basic Information
    private String symbol;
    private String name;
    private String url;
    private String isin;
    private String exchange;
    private String exchangeFullName;
    private String market;
    private String currency;
    private String sector;
    private String industry;

    // Price Data
    private BigDecimal lastPrice;
    private BigDecimal prevClose;
    private BigDecimal open;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private BigDecimal change;
    private BigDecimal changePct;
    private Long lastUpdate;

    // 52 Week Range
    private BigDecimal week52High;
    private BigDecimal week52Low;
    private String week52Range;
    private BigDecimal closeTo52weekslowPct;

    // Volume Data
    private Long volume;
    private Long avgVolume3m;

    // Bid/Ask
    private BigDecimal bid;
    private BigDecimal ask;

    // Fundamental Data
    private BigDecimal marketCap;
    private Long sharesOutstanding;
    private BigDecimal eps;
    private BigDecimal peRatio;
    private BigDecimal dividendYield;
    private BigDecimal revenue;
    private BigDecimal oneYearReturn;

    // Ratios Data
    private BigDecimal priceToBook;
    private BigDecimal debtToEquity;
    private BigDecimal profitMargin;

    // Technical Data
    private BigDecimal beta;
    private String technicalSummary1d;
    private String technicalSummary1w;
    private String technicalSummary1m;

    // Analyst Data
    private String analystConsensus;
    private Integer analystBuyCount;
    private Integer analystSellCount;
    private Integer analystHoldCount;

    // Calculated Values
    private BigDecimal grahamFairValue;
    private BigDecimal marginOfSafety;
    private BigDecimal bookValuePerShare;
}