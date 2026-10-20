package com.tunindex.market_tool.api.portfolio;

import com.tunindex.market_tool.api.services.portfolio.DayChangeCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rule that today's purchases do not earn today's move.
 *
 * <p>A share bought this morning was not held at yesterday's close, so the
 * change from that close to now happened before it was owned. Counting it
 * showed an instant paper profit on any purchase made into a rising market -
 * money the holder never made.
 *
 * <p>These cases exist because the rule was previously verified only by
 * reading the code. The live portfolio held one position predating today, so
 * the exclusion path never actually ran, and a money-affecting calculation
 * was resting on inspection alone.
 */
@DisplayName("DayChangeCalculator")
class DayChangeCalculatorTest {

    private final DayChangeCalculator calculator = new DayChangeCalculator();

    private BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("counts the full move on a position held since before today")
    void countsHeldPosition() {
        // 100 shares from last week; yesterday's close 8.00, now 8.30.
        DayChangeCalculator.DayChange change = calculator.calculate(
                n("100"), BigDecimal.ZERO, n("8.00"), n("8.30"));

        assertThat(change.applies()).isTrue();
        assertThat(change.eligibleQuantity()).isEqualByComparingTo("100");
        assertThat(change.value()).isEqualByComparingTo("30.000");
        assertThat(change.percent()).isEqualByComparingTo("3.75");
        assertThat(change.previousDayValue()).isEqualByComparingTo("800.000");
    }

    @Test
    @DisplayName("counts nothing on a position bought entirely today")
    void excludesSameDayPurchase() {
        // Bought all 100 today. The day's rise from 8.00 to 8.30 predates
        // ownership, so it is not this holder's gain.
        DayChangeCalculator.DayChange change = calculator.calculate(
                n("100"), n("100"), n("8.00"), n("8.30"));

        assertThat(change.applies()).isFalse();
        assertThat(change.eligibleQuantity()).isEqualByComparingTo("0");
        // Null, not zero: "no qualifying shares" and "the price did not move"
        // are different claims, and a zero would be averaged into the
        // portfolio total as though the day had been measured.
        assertThat(change.value()).isNull();
        assertThat(change.percent()).isNull();
    }

    @Test
    @DisplayName("counts only the shares that predate today when topping up")
    void handlesPartialTopUp() {
        // Held 100 from last week, bought 50 more today: the move applies to
        // 100 shares, not 150 and not zero. This is the case a simple
        // "bought today?" flag on the position cannot express.
        DayChangeCalculator.DayChange change = calculator.calculate(
                n("150"), n("50"), n("8.00"), n("8.30"));

        assertThat(change.eligibleQuantity()).isEqualByComparingTo("100");
        assertThat(change.value()).isEqualByComparingTo("30.000");
        // The percentage is per-share, so it is unaffected by how many
        // shares qualify.
        assertThat(change.percent()).isEqualByComparingTo("3.75");
    }

    @Test
    @DisplayName("counts a decline against the holder as readily as a gain")
    void countsLosses() {
        DayChangeCalculator.DayChange change = calculator.calculate(
                n("100"), BigDecimal.ZERO, n("8.30"), n("8.00"));

        assertThat(change.value()).isEqualByComparingTo("-30.000");
        assertThat(change.percent()).isEqualByComparingTo("-3.61");
    }

    @Test
    @DisplayName("treats an intraday round trip as leaving nothing from yesterday")
    void handlesIntradayRoundTrip() {
        // Bought 200 today and sold 150 of the older holding, so the current
        // 50 are all from today. Subtraction goes negative and must floor at
        // zero rather than inventing a negative quantity.
        DayChangeCalculator.DayChange change = calculator.calculate(
                n("50"), n("200"), n("8.00"), n("8.30"));

        assertThat(change.eligibleQuantity()).isEqualByComparingTo("0");
        assertThat(change.applies()).isFalse();
    }

    @Test
    @DisplayName("declines when yesterday's close is unknown")
    void requiresPreviousClose() {
        DayChangeCalculator.DayChange change = calculator.calculate(
                n("100"), BigDecimal.ZERO, null, n("8.30"));

        assertThat(change.applies()).isFalse();
        // The eligible count is still reported, so the client knows the
        // shares qualify and only the price reference was missing.
        assertThat(change.eligibleQuantity()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("declines on a zero or missing previous close rather than dividing")
    void refusesDegenerateInputs() {
        assertThat(calculator.calculate(n("100"), BigDecimal.ZERO, BigDecimal.ZERO, n("8.30")).applies())
                .isFalse();
        assertThat(calculator.calculate(n("100"), BigDecimal.ZERO, n("8.00"), null).applies())
                .isFalse();
        assertThat(calculator.calculate(null, null, n("8.00"), n("8.30")).applies())
                .isFalse();
    }
}
