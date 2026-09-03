package com.tunindex.market_tool.collector.dto.scoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * One stock's Tunindex Score — a transparent, rule-based buy-opportunity
 * rating. Every component is computed from real stored data (fundamentals,
 * price history, scraped headlines); nothing here is a model output or a
 * prediction. `reasons` and `warnings` quote the actual figures that drove
 * the score so a user can audit any number on screen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpportunityScoreDto {

    private String symbol;
    private String name;
    private String sector;
    private BigDecimal lastPrice;
    private String currency;

    /** 0-100, the weighted blend of the component scores below. */
    private int overallScore;

    /** STRONG_BUY | BUY | WATCH | HOLD | AVOID — thresholds on overallScore. */
    private String verdict;

    // Component scores, each 0-100. Null when the inputs for that component
    // are missing entirely, so the UI can show "no data" instead of a zero
    // that would read as "scored badly".
    private Integer valuationScore;
    private Integer financialHealthScore;
    private Integer timingScore;
    private Integer incomeScore;
    private Integer momentumScore;
    private Integer newsScore;

    /**
     * 0-100: how much of the scorer's expected input was actually available
     * for this stock. A high score built on thin data deserves less trust,
     * and hiding that would be misleading.
     */
    private int dataCompleteness;

    private List<String> reasons;
    private List<String> warnings;
}
