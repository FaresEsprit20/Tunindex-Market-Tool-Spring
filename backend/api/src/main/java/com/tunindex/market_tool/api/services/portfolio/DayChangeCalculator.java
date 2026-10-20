package com.tunindex.market_tool.api.services.portfolio;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Today's move on a position, counted only on shares that were actually
 * exposed to it.
 *
 * <p>Extracted from the portfolio assembly so it can be tested directly.
 * Verifying it in place meant standing up a mocked HTTP client just to reach
 * the arithmetic, which is why the rule below went unverified longer than a
 * money-affecting calculation should.
 *
 * <p><b>The rule.</b> A share bought this morning was not held at yesterday's
 * close, so the move from that close to now is not a gain or loss its owner
 * experienced. Counting it credits a position with profit it never earned -
 * buy on a green day and the portfolio instantly shows the whole day's rise.
 *
 * <p>Eligibility is derived from today's purchases rather than a flag on the
 * position, which handles the partial case that a flag cannot: holding 100
 * shares from last week and buying 50 more today counts the day's move on
 * 100 - not 150, and not zero.
 */
@Service
public class DayChangeCalculator {

    private static final int MONEY_SCALE = 3;
    private static final int PERCENT_SCALE = 2;

    /**
     * @param eligibleQuantity shares held through yesterday's close
     * @param value            money moved on those shares, or null if none qualify
     * @param percent          per-share move against the previous close
     * @param previousDayValue what the eligible shares were worth at that close,
     *                         so a portfolio total can be weighted by size
     *                         instead of averaging percentages
     */
    public record DayChange(
            BigDecimal eligibleQuantity,
            BigDecimal value,
            BigDecimal percent,
            BigDecimal previousDayValue) {

        public boolean applies() {
            return value != null;
        }
    }

    /**
     * @param quantity     shares held now
     * @param boughtToday  shares bought today, which do not qualify
     * @param previousClose yesterday's close, or null when unknown
     * @param currentPrice  the price now
     */
    public DayChange calculate(BigDecimal quantity, BigDecimal boughtToday,
                               BigDecimal previousClose, BigDecimal currentPrice) {

        BigDecimal held = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal newToday = boughtToday == null ? BigDecimal.ZERO : boughtToday;

        BigDecimal eligible = held.subtract(newToday);
        if (eligible.signum() < 0) {
            // More bought today than are held now, so the remainder was sold
            // intraday and nothing survives from yesterday.
            eligible = BigDecimal.ZERO;
        }

        if (previousClose == null || previousClose.signum() <= 0
                || currentPrice == null || eligible.signum() <= 0) {
            // Left null rather than zero: "no qualifying shares" and "no move"
            // are different statements, and a zero would be averaged into a
            // portfolio total as though the day had been measured.
            return new DayChange(eligible, null, null, BigDecimal.ZERO);
        }

        BigDecimal perShare = currentPrice.subtract(previousClose);

        return new DayChange(
                eligible,
                perShare.multiply(eligible).setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                perShare.divide(previousClose, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(PERCENT_SCALE, RoundingMode.HALF_UP),
                previousClose.multiply(eligible).setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }
}
