package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.services.scoring.ReversalDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The distinction this exists to draw: a stock that has fallen versus a stock
 * that has stopped falling.
 *
 * <p>Both look identical to an oversold indicator — a falling stock is
 * oversold the entire way down, which is exactly when buying hurts most. The
 * old timing score averaged its indicators, so a knife and a bottom landed
 * within a few points of each other. These tests fail if that collapses back.
 */
@DisplayName("ReversalDetector")
class ReversalDetectorTest {

    private final ReversalDetector detector = new ReversalDetector();

    /** Bars ending today, one per day, following the given closes. */
    private List<PriceHistory> bars(double... closes) {
        List<PriceHistory> history = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(closes.length - 1L);
        for (int i = 0; i < closes.length; i++) {
            PriceHistory bar = new PriceHistory();
            bar.setSymbol("TEST");
            bar.setTradeDate(start.plusDays(i));
            bar.setClose(BigDecimal.valueOf(closes[i]));
            bar.setVolume(1000L);
            history.add(bar);
        }
        return history;
    }

    /** A steady decline: every leg lower than the last. */
    private List<PriceHistory> fallingSeries() {
        double[] closes = new double[60];
        for (int i = 0; i < closes.length; i++) {
            closes[i] = 100 - i * 1.2;
        }
        return bars(closes);
    }

    /** Falls, troughs, then turns up — a higher low after the decline. */
    private List<PriceHistory> bottomingSeries() {
        double[] closes = new double[60];
        for (int i = 0; i < 40; i++) {
            closes[i] = 100 - i * 1.5;      // down to ~41
        }
        for (int i = 40; i < 60; i++) {
            closes[i] = 41 + (i - 40) * 0.8; // recovering
        }
        return bars(closes);
    }

    private TechnicalAnalysisDto technical(double rsi, double sma20, double sma50,
                                           String macdCross, double histogram,
                                           double stochK, double stochD) {
        return TechnicalAnalysisDto.builder()
                .rsi14(BigDecimal.valueOf(rsi))
                .sma20(BigDecimal.valueOf(sma20))
                .sma50(BigDecimal.valueOf(sma50))
                .macdCrossSignal(macdCross)
                .macdHistogram(BigDecimal.valueOf(histogram))
                .macdLine(BigDecimal.valueOf(-1))
                .macdSignal(BigDecimal.valueOf(-1.5))
                .stochasticK(BigDecimal.valueOf(stochK))
                .stochasticD(BigDecimal.valueOf(stochD))
                .build();
    }

    @Test
    @DisplayName("a falling knife is not called a reversal, however oversold")
    void fallingKnifeIsNotAReversal() {
        // Deeply oversold, at the lows, still going down. This is the case the
        // averaging score rewarded and a buyer regrets.
        ReversalDetector.ReversalSignal signal = detector.detect(
                fallingSeries(),
                technical(22, 40, 55, "BEARISH_CROSS", -2.5, 12, 18));

        assertThat(signal.phase()).isEqualTo(ReversalDetector.Phase.DOWNTREND);
        assertThat(signal.isActionable()).isFalse();
    }

    @Test
    @DisplayName("a genuine turn after a decline scores far higher than the knife")
    void bottomingOutbidsFallingKnife() {
        ReversalDetector.ReversalSignal knife = detector.detect(
                fallingSeries(),
                technical(22, 40, 55, "BEARISH_CROSS", -2.5, 12, 18));

        ReversalDetector.ReversalSignal turning = detector.detect(
                bottomingSeries(),
                technical(45, 50, 58, "BULLISH_CROSS", -0.2, 35, 28));

        // The whole point of the component: these two must not be neighbours.
        assertThat(turning.score()).isGreaterThan(knife.score());
        assertThat(turning.isActionable()).isTrue();
    }

    @Test
    @DisplayName("names the conditions that fired, so a score can be argued with")
    void explainsItself() {
        ReversalDetector.ReversalSignal signal = detector.detect(
                bottomingSeries(),
                technical(45, 50, 58, "BULLISH_CROSS", -0.2, 35, 28));

        assertThat(signal.conditions()).isNotEmpty();
        assertThat(String.join(" ", signal.conditions())).containsIgnoringCase("MACD");
    }

    @Test
    @DisplayName("a stock that never fell is not a reversal candidate")
    void requiresADeclineToReverse() {
        double[] rising = new double[60];
        for (int i = 0; i < rising.length; i++) {
            rising[i] = 50 + i * 0.9;
        }

        ReversalDetector.ReversalSignal signal = detector.detect(
                bars(rising),
                technical(62, 90, 75, "BULLISH_CROSS", 0.4, 70, 65));

        // Bullish signals in an advance describe a move already under way -
        // a different, later thing to buy than a bottom.
        assertThat(signal.phase()).isIn(ReversalDetector.Phase.UPTREND, ReversalDetector.Phase.TOPPING);
    }

    @Test
    @DisplayName("flags an extended stock losing momentum as topping")
    void detectsTopping() {
        double[] rising = new double[60];
        for (int i = 0; i < rising.length; i++) {
            rising[i] = 50 + i * 0.9;
        }

        ReversalDetector.ReversalSignal signal = detector.detect(
                bars(rising),
                technical(78, 95, 80, "NONE", 0.1, 88, 85));

        assertThat(signal.phase()).isEqualTo(ReversalDetector.Phase.TOPPING);
    }

    @Test
    @DisplayName("declines to judge on too little history")
    void refusesShortHistory() {
        ReversalDetector.ReversalSignal signal = detector.detect(
                bars(10, 9, 8, 7, 6),
                technical(20, 7, 9, "BULLISH_CROSS", -0.1, 15, 12));

        assertThat(signal.phase()).isEqualTo(ReversalDetector.Phase.NEUTRAL);
        assertThat(signal.score()).isZero();
    }

    @Test
    @DisplayName("tolerates missing technicals rather than throwing")
    void toleratesMissingInputs() {
        assertThat(detector.detect(fallingSeries(), null).phase())
                .isEqualTo(ReversalDetector.Phase.NEUTRAL);
        assertThat(detector.detect(null, technical(30, 40, 50, "NONE", -1, 20, 25)).phase())
                .isEqualTo(ReversalDetector.Phase.NEUTRAL);
    }
}
