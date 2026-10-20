package com.tunindex.market_tool.api.dto.scoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Passthrough of the collector's OpportunityScoreDto — one stock's
 * Tunindex Score, its component breakdown, and the plain-language reasons
 * and warnings behind it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityScoreResponseDto {

    private String symbol;
    private String name;
    private String sector;
    private BigDecimal lastPrice;
    private String currency;

    private int overallScore;
    private String verdict;

    private Integer valuationScore;
    private Integer financialHealthScore;
    private Integer timingScore;
    private Integer incomeScore;
    private Integer momentumScore;
    private Integer newsScore;

    private int dataCompleteness;

    private List<String> reasons;
    private List<String> warnings;

    /**
     * Where to buy it, from the Tradify Analyst.
     *
     * <p>Travels with the score so the opportunities list can show a level
     * rather than only a verdict - "BUY" without a price is the half of the
     * decision the reader cannot act on.
     */
    private com.tunindex.market_tool.api.dto.analyst.TradeSetupResponseDto tradeSetup;
}
