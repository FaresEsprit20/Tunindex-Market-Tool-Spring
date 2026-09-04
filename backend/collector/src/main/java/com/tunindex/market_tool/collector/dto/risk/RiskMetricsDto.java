package com.tunindex.market_tool.collector.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Risk profile of one name, computed from the daily closes we store.
 *
 * <p>Everything here is derived from a single return series. Where a figure
 * cannot be computed honestly it comes back null rather than zero — a stock
 * with 12 observations has no meaningful beta, and reporting 0.00 would read
 * as "moves independently of the market", which is a much stronger claim than
 * "we do not know".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskMetricsDto {

    private String symbol;

    /** Trading days actually used — the sample every figure below rests on. */
    private int observations;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    /** Standard deviation of daily returns, annualised by sqrt(252), in percent. */
    private BigDecimal annualisedVolatilityPct;

    /** Standard deviation of negative daily returns only, annualised, in percent. */
    private BigDecimal downsideDeviationPct;

    /** Largest peak-to-trough fall over the window, as a negative percent. */
    private BigDecimal maxDrawdownPct;

    private LocalDate maxDrawdownPeak;
    private LocalDate maxDrawdownTrough;

    /** Total return over the window, in percent. */
    private BigDecimal periodReturnPct;

    /** Period return scaled to a year, in percent. */
    private BigDecimal annualisedReturnPct;

    /**
     * Slope of this name's returns against the equal-weighted market series.
     * Null when the overlap with the market series is too short to be honest.
     */
    private BigDecimal beta;

    /**
     * Share of this name's variance explained by the market, 0..1 — the R² of
     * the beta regression.
     *
     * <p>Not named {@code rSquared}: Lombok would generate
     * {@code getRSquared()}, whose implicit Java Beans name is "rsquared",
     * which does not match the field's own implicit name — so Jackson emits
     * the value twice under two different keys. This name has no such quirk,
     * and states what the figure means rather than what it is called.
     */
    private BigDecimal varianceExplained;

    /** (annualised return - risk free) / annualised volatility. */
    private BigDecimal sharpeRatio;

    /** Same numerator, but divided by downside deviation only. */
    private BigDecimal sortinoRatio;

    /** The rate used in the two ratios above, in percent — stated so it can be shown. */
    private BigDecimal riskFreeRatePct;

    /** Historical 5th percentile of daily returns: the 1-day 95% VaR, negative. */
    private BigDecimal valueAtRisk95Pct;

    /** Mean of the returns at or below that percentile — the expected tail loss. */
    private BigDecimal conditionalVar95Pct;

    private BigDecimal bestDayPct;
    private BigDecimal worstDayPct;

    /** Share of days that closed up, in percent. */
    private BigDecimal positiveDaysPct;

    /**
     * Plain-language notes on how these numbers were produced and what limits
     * them, rendered verbatim by the UI so a figure is never shown without its
     * caveats.
     */
    private List<String> methodology;
}
