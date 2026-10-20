package com.tunindex.market_tool.collector.dto.backtest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * What actually happened after each timing-score band.
 *
 * <p>Read {@code baselineWinRate} before any band's win rate: a 60% hit
 * rate is worthless if the market rose in 60% of all windows. The number
 * that matters is a band's edge <em>over</em> the baseline, which is why
 * both travel together in this payload and on screen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDto {

    private int horizonDays;

    /** Trading days between one evaluation date and the next, per symbol. */
    private int stepDays;

    private int symbolsTested;
    private int totalObservations;

    /** Share of all observations with a positive forward return, 0-100. */
    private BigDecimal baselineWinRate;
    private BigDecimal baselineAvgReturnPct;

    private List<BandResultDto> bands;

    /**
     * Plain-language statement of what this run can and cannot support.
     * Rendered verbatim in the UI — a backtest without its caveats
     * attached is a number people will over-trust.
     */
    private List<String> methodology;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BandResultDto {
        private String label;
        private int minScore;
        private int maxScore;
        private int observations;
        private BigDecimal winRate;
        private BigDecimal avgReturnPct;
        private BigDecimal medianReturnPct;
        /** winRate minus baselineWinRate — the only figure worth acting on. */
        private BigDecimal edgeOverBaseline;
    }
}
