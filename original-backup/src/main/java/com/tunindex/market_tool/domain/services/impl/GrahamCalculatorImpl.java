package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.domain.services.calculator.GrahamCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Override
    public BigDecimal calculateCloseTo52WeekLowPercentage(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High) {
        if (currentPrice == null || week52Low == null) {
            return null;
        }

        try {
            if (week52High != null && week52High.compareTo(week52Low) > 0) {
                BigDecimal range = week52High.subtract(week52Low);
                BigDecimal position = currentPrice.subtract(week52Low);
                BigDecimal positionPct = position.divide(range, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                return positionPct.setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal percentageAboveLow = currentPrice.subtract(week52Low)
                        .divide(week52Low, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                return percentageAboveLow.setScale(2, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
            log.error("Failed to calculate 52-week low percentage: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public BigDecimal calculateDistanceFrom52WeekLow(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High) {
        BigDecimal positionPct = calculateCloseTo52WeekLowPercentage(currentPrice, week52Low, week52High);
        if (positionPct == null) {
            return null;
        }
        BigDecimal distance = new BigDecimal("100").subtract(positionPct);
        return distance.setScale(2, RoundingMode.HALF_UP);
    }
}