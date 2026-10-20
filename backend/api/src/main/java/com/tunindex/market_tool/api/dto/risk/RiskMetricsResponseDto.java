package com.tunindex.market_tool.api.dto.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API-facing mirror of the collector's RiskMetricsDto.
 *
 * <p>Deliberately a separate type rather than a shared one: the collector is
 * free to add internal fields without those leaking into the public contract,
 * and {@code @JsonIgnoreProperties} means a new field upstream cannot break
 * deserialisation here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskMetricsResponseDto {

    private String symbol;

    /** Trading days behind every figure below. */
    private int observations;

    private LocalDate periodStart;
    private LocalDate periodEnd;

    private BigDecimal annualisedVolatilityPct;
    private BigDecimal downsideDeviationPct;
    private BigDecimal maxDrawdownPct;
    private LocalDate maxDrawdownPeak;
    private LocalDate maxDrawdownTrough;
    private BigDecimal periodReturnPct;
    private BigDecimal annualisedReturnPct;

    /** Null when the overlap with the market series is too short to be honest. */
    private BigDecimal beta;

    /**
     * Share of variance explained by the market, 0..1 — the R² of the beta
     * regression. Named for what it means rather than "rSquared", which
     * Jackson serialises under two conflicting keys (see the collector DTO).
     */
    private BigDecimal varianceExplained;

    private BigDecimal sharpeRatio;
    private BigDecimal sortinoRatio;

    /** The rate the two ratios were computed against, in percent. */
    private BigDecimal riskFreeRatePct;

    private BigDecimal valueAtRisk95Pct;
    private BigDecimal conditionalVar95Pct;
    private BigDecimal bestDayPct;
    private BigDecimal worstDayPct;
    private BigDecimal positiveDaysPct;

    /** Rendered verbatim by the UI so no figure is shown without its caveats. */
    private List<String> methodology;
}
