package com.tunindex.market_tool.collector.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Sector-relative fundamental scoring, computed from real, currently-stored
 * Stock rows (this stock's own fields plus a live AVG(...) over its sector
 * peers) — no external "analyst score" is scraped or copied; the formula
 * for every score here lives in FundamentalAnalysisCalculator and is fully
 * auditable.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundamentalAnalysisDto {

    private BigDecimal peRatio;
    private BigDecimal sectorAvgPeRatio;
    private BigDecimal dividendYield;
    private BigDecimal sectorAvgDividendYield;
    private BigDecimal debtToEquity;
    private BigDecimal sectorAvgDebtToEquity;
    private BigDecimal profitMargin;
    private BigDecimal sectorAvgProfitMargin;
    private BigDecimal priceToBook;
    private BigDecimal sectorAvgPriceToBook;
    private int sectorPeerCount;

    private int valuationScore;
    private int profitabilityScore;
    private int financialHealthScore;
    private int incomeScore;
    private int overallScore;
    private String overallRating; // STRONG | MODERATE | WEAK
}
