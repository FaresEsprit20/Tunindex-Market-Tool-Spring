package com.tunindex.market_tool.domain.dto.providers.investingcom;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Slf4j
public class FairValueCalculator {

    /**
     * Graham's formula: sqrt(22.5 * EPS * BVPS)
     * @param eps Earnings Per Share
     * @param bvps Book Value Per Share
     * @return Graham Fair Value, or null if invalid inputs
     */
    public BigDecimal grahamFairValue(BigDecimal eps, BigDecimal bvps) {
        if (eps == null || bvps == null) {
            log.debug("Cannot calculate Graham Fair Value - EPS or BVPS is null");
            return null;
        }

        if (eps.compareTo(BigDecimal.ZERO) < 0 || bvps.compareTo(BigDecimal.ZERO) < 0) {
            log.debug("Cannot calculate Graham Fair Value - EPS or BVPS is negative");
            return null;
        }

        try {
            // 22.5 * EPS * BVPS
            BigDecimal twentyTwoPointFive = new BigDecimal("22.5");
            BigDecimal product = twentyTwoPointFive.multiply(eps).multiply(bvps);

            // Square root
            double sqrtValue = Math.sqrt(product.doubleValue());
            BigDecimal result = BigDecimal.valueOf(sqrtValue);

            return result.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to calculate Graham Fair Value: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculate margin of safety as percentage
     * @param price Current stock price
     * @param fairValue Fair value of the stock
     * @return Margin of safety percentage (e.g., 37.3 means 37.3% undervalued)
     */
    public BigDecimal marginOfSafety(BigDecimal price, BigDecimal fairValue) {
        if (price == null || fairValue == null || fairValue.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Cannot calculate Margin of Safety - invalid inputs");
            return null;
        }

        try {
            // ((fairValue - price) / fairValue) * 100
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
     * Calculate current price's position within the 52-week range
     * @param currentPrice Current stock price
     * @param week52Low 52-week low
     * @param week52High 52-week high (optional)
     * @return Position percentage where:
     *         0% = at 52-week low
     *         100% = at 52-week high
     *         Values between 0-100% show position between low and high
     */
    public BigDecimal closeTo52WeekLowPercentage(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High) {
        if (currentPrice == null || week52Low == null) {
            return null;
        }

        try {
            if (week52High != null && week52High.compareTo(week52Low) > 0) {
                // Calculate position between low and high (0-100% scale)
                BigDecimal range = week52High.subtract(week52Low);
                BigDecimal position = currentPrice.subtract(week52Low);
                BigDecimal positionPct = position.divide(range, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                return positionPct.setScale(2, RoundingMode.HALF_UP);
            } else {
                // Fallback: calculate percentage above low
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

    /**
     * Calculate distance from 52-week low (opposite of position)
     * @param currentPrice Current stock price
     * @param week52Low 52-week low
     * @param week52High 52-week high
     * @return Distance percentage from 52-week low
     */
    public BigDecimal distanceFrom52WeekLow(BigDecimal currentPrice, BigDecimal week52Low, BigDecimal week52High) {
        BigDecimal positionPct = closeTo52WeekLowPercentage(currentPrice, week52Low, week52High);
        if (positionPct == null) {
            return null;
        }

        // Distance from low = 100% - position percentage
        BigDecimal distance = new BigDecimal("100").subtract(positionPct);
        return distance.setScale(2, RoundingMode.HALF_UP);
    }
}