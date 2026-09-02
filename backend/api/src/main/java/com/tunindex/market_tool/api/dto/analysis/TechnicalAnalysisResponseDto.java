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
public class TechnicalAnalysisResponseDto {

    private int dataPointsUsed;
    private BigDecimal lastClose;

    private BigDecimal sma20;
    private BigDecimal sma50;
    private String trendSignal;

    private BigDecimal rsi14;
    private String rsiSignal;

    private BigDecimal macdLine;
    private BigDecimal macdSignal;
    private BigDecimal macdHistogram;
    private String macdCrossSignal;

    private BigDecimal bollingerUpper;
    private BigDecimal bollingerMiddle;
    private BigDecimal bollingerLower;

    private BigDecimal volatilityAnnualizedPct;

    private BigDecimal stochasticK;
    private BigDecimal stochasticD;
    private String stochasticSignal;

    private BigDecimal williamsR;
    private String williamsRSignal;

    private BigDecimal atr14;

    private BigDecimal adx14;
    private String adxSignal;
}
