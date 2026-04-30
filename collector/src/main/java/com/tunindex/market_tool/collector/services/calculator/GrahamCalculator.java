package com.tunindex.market_tool.collector.services.calculator;

import java.math.BigDecimal;

public interface GrahamCalculator {

    /**
     * Graham's formula: sqrt(22.5 * EPS * BVPS)
     */
    BigDecimal calculateGrahamFairValue(BigDecimal eps, BigDecimal bvps);

    /**
     * Calculate margin of safety as percentage
     */
    BigDecimal calculateMarginOfSafety(BigDecimal price, BigDecimal fairValue);

    /**
     * Calculate current price's position within the 52-week range
     */
    BigDecimal calculateCloseTo52WeekLowPercentage(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High);

    /**
     * Calculate distance from 52-week low
     */
    BigDecimal calculateDistanceFrom52WeekLow(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High);
}