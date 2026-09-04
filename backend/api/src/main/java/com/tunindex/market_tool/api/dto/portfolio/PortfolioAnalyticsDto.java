package com.tunindex.market_tool.api.dto.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * How a portfolio is actually built, as opposed to what it is worth.
 *
 * <p>The summary endpoint answers "am I up?"; this one answers "what am I
 * exposed to, and how badly would one bad name hurt?" — concentration, sector
 * tilt, projected income and the risk carried by the weights.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioAnalyticsDto {

    private int positionCount;
    private BigDecimal totalMarketValue;
    private BigDecimal cashBalance;

    /** Cash as a share of total portfolio value, in percent. */
    private BigDecimal cashWeightPct;

    /**
     * Herfindahl-Hirschman index of position weights, 0..10000. A single
     * holding scores 10000; ten equal holdings score 1000. Computed on the
     * invested book only — cash is not a concentration risk.
     */
    private Integer concentrationHhi;

    /** Plain reading of the HHI: DIVERSIFIED | MODERATE | CONCENTRATED. */
    private String concentrationLabel;

    /**
     * Number of equally-weighted positions that would produce the same HHI.
     * More intuitive than the index: "your 8 holdings behave like 3.2".
     */
    private BigDecimal effectivePositions;

    /** Weight of the single largest position, in percent. */
    private BigDecimal largestPositionPct;

    private String largestPositionSymbol;

    /** Weight of the largest sector, in percent. */
    private BigDecimal largestSectorPct;

    private String largestSectorName;

    private List<PortfolioWeightDto> positionWeights;
    private List<PortfolioWeightDto> sectorWeights;

    /**
     * Value-weighted average beta of the holdings. Null when we have beta for
     * too little of the book to average honestly — {@link #betaCoveragePct}
     * says how much of it was covered.
     */
    private BigDecimal weightedBeta;

    private BigDecimal betaCoveragePct;

    /** Projected annual dividend income in TND at today's yields. */
    private BigDecimal projectedAnnualIncome;

    /** That income as a share of the invested book, in percent. */
    private BigDecimal portfolioYieldPct;

    /** Share of the book we actually hold a dividend yield for, in percent. */
    private BigDecimal incomeCoveragePct;

    private List<PortfolioIncomeDto> incomeByPosition;

    /**
     * Concrete observations about this book — concentration, sector tilt,
     * uncovered risk. Written server-side so the same wording appears wherever
     * the analytics are shown.
     */
    private List<String> observations;
}
