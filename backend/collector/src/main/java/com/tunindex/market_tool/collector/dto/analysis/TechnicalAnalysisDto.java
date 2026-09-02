package com.tunindex.market_tool.collector.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Every field here is computed from PriceHistory (real scraped OHLCV) using
 * the standard published formulas for each indicator — nothing is scraped
 * pre-computed from a third party, so the exact math is ours to verify.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalAnalysisDto {

    private int dataPointsUsed;
    private BigDecimal lastClose;

    private BigDecimal sma20;
    private BigDecimal sma50;
    private String trendSignal; // BULLISH | BEARISH | NEUTRAL — price vs SMA20/SMA50

    private BigDecimal rsi14;
    private String rsiSignal; // OVERBOUGHT | OVERSOLD | NEUTRAL

    private BigDecimal macdLine;
    private BigDecimal macdSignal;
    private BigDecimal macdHistogram;
    private String macdCrossSignal; // BULLISH_CROSS | BEARISH_CROSS | NONE

    private BigDecimal bollingerUpper;
    private BigDecimal bollingerMiddle;
    private BigDecimal bollingerLower;

    private BigDecimal volatilityAnnualizedPct;
}
