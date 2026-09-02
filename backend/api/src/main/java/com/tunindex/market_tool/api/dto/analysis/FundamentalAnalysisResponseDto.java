package com.tunindex.market_tool.api.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FundamentalAnalysisResponseDto {

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
    private String overallRating;
}
