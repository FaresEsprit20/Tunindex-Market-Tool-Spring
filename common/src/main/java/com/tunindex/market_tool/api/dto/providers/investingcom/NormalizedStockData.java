package com.tunindex.market_tool.api.dto.providers.investingcom;

import com.tunindex.market_tool.api.entities.enums.OwnershipType;
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
    private OwnershipType ownershipType;
    private String country;  // NEW - Country of the stock

    // Price Data
    private BigDecimal lastPrice;
    private BigDecimal prevClose;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
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
    private BigDecimal priceToSales;      // NEW - P/S Ratio
    private BigDecimal payoutRatio;       // NEW - Dividend Payout Ratio
    private BigDecimal returnOnEquity;   // NEW - ROE
    private BigDecimal returnOnAssets;   // NEW - ROA
    private BigDecimal operatingMargin;  // NEW - Operating Margin

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

    // Balance Sheet Data
    private BigDecimal totalEquity;
    private BigDecimal netIncome;

    // Source
    private String source;
}