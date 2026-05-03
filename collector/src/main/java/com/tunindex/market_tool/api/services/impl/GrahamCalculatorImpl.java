package com.tunindex.market_tool.api.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.tunindex.market_tool.api.services.calculator.GrahamCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
public class GrahamCalculatorImpl implements GrahamCalculator {

    @Override
    public BigDecimal calculateGrahamFairValue(BigDecimal eps, BigDecimal bvps) {
        if (eps == null || bvps == null) {
            log.debug("Cannot calculate Graham Fair Value - EPS or BVPS is null");
            return null;
        }

        if (eps.compareTo(BigDecimal.ZERO) < 0 || bvps.compareTo(BigDecimal.ZERO) < 0) {
            log.debug("Cannot calculate Graham Fair Value - EPS or BVPS is negative");
            return null;
        }

        try {
            BigDecimal twentyTwoPointFive = new BigDecimal("22.5");
            BigDecimal product = twentyTwoPointFive.multiply(eps).multiply(bvps);
            double sqrtValue = Math.sqrt(product.doubleValue());
            BigDecimal result = BigDecimal.valueOf(sqrtValue);
            return result.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to calculate Graham Fair Value: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public BigDecimal calculateMarginOfSafety(BigDecimal price, BigDecimal fairValue) {
        if (price == null || fairValue == null || fairValue.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Cannot calculate Margin of Safety - invalid inputs");
            return null;
        }

        try {
            BigDecimal difference = fairValue.subtract(price);
            BigDecimal ratio = difference.divide(fairValue, 4, RoundingMode.HALF_UP);
            BigDecimal percentage = ratio.multiply(new BigDecimal("100"));
            return percentage.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to calculate Margin of Safety: {}", e.getMessage());
            return null;
        }
    }

    /**
     * How close the current price is to the 52-week LOW.
     *
     * 100% → price is AT the 52-week low  (maximally close to low)
     *   0% → price is AT the 52-week high (maximally far from low)
     *
     * Formula: (1 - (price - low) / (high - low)) * 100
     */
    @Override
    public BigDecimal calculateCloseTo52WeekLowPercentage(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High) {
        if (currentPrice == null || week52Low == null) {
            return null;
        }

        try {
            if (week52High != null && week52High.compareTo(week52Low) > 0) {
                BigDecimal range = week52High.subtract(week52Low);
                BigDecimal positionFromLow = currentPrice.subtract(week52Low);
                // (1 - positionFromLow/range) * 100  →  100% at low, 0% at high
                BigDecimal closenessToLow = BigDecimal.ONE
                        .subtract(positionFromLow.divide(range, 4, RoundingMode.HALF_UP))
                        .multiply(new BigDecimal("100"));
                return closenessToLow.setScale(2, RoundingMode.HALF_UP);
            } else {
                // No valid high available — fall back to plain % above low:
                // price AT low → 0% above → closeness = 100%, price rising → closeness drops
                BigDecimal percentageAboveLow = currentPrice.subtract(week52Low)
                        .divide(week52Low, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                // Invert: 100% - %aboveLow (clamped to 0 minimum)
                BigDecimal closenessToLow = new BigDecimal("100").subtract(percentageAboveLow);
                return closenessToLow.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.error("Failed to calculate close-to-52-week-low percentage: {}", e.getMessage());
            return null;
        }
    }

    /**
     * How close the current price is to the 52-week HIGH.
     *
     * 100% → price is AT the 52-week high (maximally close to high)
     *   0% → price is AT the 52-week low  (maximally far from high)
     *
     * Formula: (price - low) / (high - low) * 100
     * (exact inverse of closeTo52WeekLow)
     */
    @Override
    public BigDecimal calculateDistanceFrom52WeekLow(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High) {
        if (currentPrice == null || week52Low == null) {
            return null;
        }

        try {
            if (week52High != null && week52High.compareTo(week52Low) > 0) {
                BigDecimal range = week52High.subtract(week52Low);
                BigDecimal positionFromLow = currentPrice.subtract(week52Low);
                // positionFromLow/range * 100  →  0% at low, 100% at high
                BigDecimal closenessToHigh = positionFromLow
                        .divide(range, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                return closenessToHigh.setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal percentageAboveLow = currentPrice.subtract(week52Low)
                        .divide(week52Low, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                return percentageAboveLow.setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.error("Failed to calculate close-to-52-week-high percentage: {}", e.getMessage());
            return null;
        }
    }
}
