package com.tunindex.market_tool.collector.services.scoring;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether a decline looks finished, rather than merely deep.
 *
 * <p>The timing score used to average its indicators, which cannot express the
 * thing that actually matters for an entry. A falling stock is oversold the
 * whole way down: RSI below 30 says "this has fallen hard", not "this has
 * stopped falling". Averaging an oversold reading with four neutral ones also
 * buries it - five 50s and one 95 come out near 57, so a genuine setup and an
 * unremarkable stock score almost the same.
 *
 * <p>A turn is a <em>conjunction</em>, not an average. What distinguishes a
 * bottom from a knife is several independent things agreeing at once: the
 * stock was in a downtrend, it reached exhaustion, momentum has turned up, and
 * price has started confirming. So this counts confirmed conditions instead of
 * averaging scores, and it will not call a reversal at all unless the decline
 * it is reversing actually happened.
 *
 * <p>Two guards keep it honest. It requires downtrend context, so a stock
 * grinding to new highs is never labelled a "reversal" just because its MACD
 * ticked up. And it reports the conditions it found by name, so a score can be
 * argued with rather than taken on faith.
 *
 * <p>This is pattern recognition on public indicators, not prediction. A
 * confirmed setup is a better-than-average moment to look, not a forecast.
 */
@Service
@Slf4j
public class ReversalDetector {

    /** Where a stock sits in the cycle, which is what the score means. */
    public enum Phase {
        /** Falling, no sign of a floor. The dangerous place to buy. */
        DOWNTREND,
        /** Fell hard and is showing exhaustion, but has not turned yet. */
        BOTTOMING,
        /** Downtrend broken and momentum confirming: the entry window. */
        REVERSING,
        /** Already advancing - the move is under way, not beginning. */
        UPTREND,
        /** Extended and losing momentum. */
        TOPPING,
        /** No clear structure, or not enough history to judge. */
        NEUTRAL
    }

    /**
     * @param score      0-100, how strong the case for an entry is now
     * @param phase      where in the cycle the stock sits
     * @param conditions the checks that fired, in plain language
     */
    public record ReversalSignal(int score, Phase phase, List<String> conditions) {
        public boolean isActionable() {
            return phase == Phase.REVERSING || phase == Phase.BOTTOMING;
        }
    }

    /** Bars needed before structure can be judged at all. */
    private static final int MIN_BARS = 40;

    /** Window for "recently oversold" and for locating swing lows. */
    private static final int RECENT_WINDOW = 20;

    /** Longer window, for comparing this low against the previous one. */
    private static final int PRIOR_WINDOW = 45;

    private static final double OVERSOLD_RSI = 35;
    private static final double OVERBOUGHT_RSI = 70;

    public ReversalSignal detect(List<PriceHistory> history, TechnicalAnalysisDto technical) {
        List<PriceHistory> bars = usable(history);
        if (bars.size() < MIN_BARS || technical == null) {
            return new ReversalSignal(0, Phase.NEUTRAL, List.of());
        }

        List<String> conditions = new ArrayList<>();
        double last = close(bars.get(bars.size() - 1));

        // ── Context: was this actually falling? ───────────────────────────
        // Without this, every mild uptick in a rising stock reads as a
        // "reversal" and the signal means nothing.
        boolean downtrend = inDowntrend(bars, technical);
        boolean uptrend = inUptrend(bars, technical);

        int points = 0;

        // ── Exhaustion: has the decline gone far enough to turn? ──────────
        Double rsi = value(technical.getRsi14());
        boolean oversoldNow = rsi != null && rsi < OVERSOLD_RSI;
        if (oversoldNow) {
            points += 15;
            conditions.add(String.format("RSI %.0f — oversold", rsi));
        }

        if (nearLowerBand(last, technical)) {
            points += 10;
            conditions.add("Price at the lower Bollinger band");
        }

        // ── The turn: is momentum actually rising, not just low? ──────────
        // This is the distinction the old average could not draw.
        if (momentumTurningUp(technical)) {
            points += 25;
            conditions.add("MACD histogram turning up");
        }

        if (stochasticCrossingUp(technical)) {
            points += 15;
            conditions.add("Stochastic crossing up from oversold");
        }

        // ── Confirmation: is price agreeing with momentum? ────────────────
        if (higherLow(bars)) {
            points += 20;
            conditions.add("Higher low than the previous swing");
        }

        if (bullishDivergence(bars, technical)) {
            points += 25;
            conditions.add("Bullish divergence — lower price low, higher RSI low");
        }

        if (reclaimedShortAverage(last, technical)) {
            points += 15;
            conditions.add("Price back above its 20-day average");
        }

        // ── Verdict ──────────────────────────────────────────────────────
        Phase phase = phase(downtrend, uptrend, points, rsi, conditions);

        // Only a decline can be reversed. Signals found in a stock that was
        // never falling describe an advance already in progress, which is a
        // different (and later) thing to buy.
        int score = downtrend ? Math.min(points, 100) : Math.min(points / 2, 50);

        return new ReversalSignal(score, phase, List.copyOf(conditions));
    }

    private Phase phase(boolean downtrend, boolean uptrend, int points, Double rsi, List<String> conditions) {
        if (downtrend && points >= 55) {
            return Phase.REVERSING;
        }
        if (downtrend && points >= 25) {
            return Phase.BOTTOMING;
        }
        if (downtrend) {
            conditions.add("Still in a downtrend with no turn confirmed");
            return Phase.DOWNTREND;
        }
        if (uptrend && rsi != null && rsi > OVERBOUGHT_RSI) {
            return Phase.TOPPING;
        }
        if (uptrend) {
            return Phase.UPTREND;
        }
        return Phase.NEUTRAL;
    }

    /** Below both averages, with the short one below the long one. */
    private boolean inDowntrend(List<PriceHistory> bars, TechnicalAnalysisDto technical) {
        Double sma20 = value(technical.getSma20());
        Double sma50 = value(technical.getSma50());
        double last = close(bars.get(bars.size() - 1));

        if (sma20 != null && sma50 != null) {
            return last < sma50 && sma20 < sma50;
        }
        // Without the averages, fall back to the plain question: is price
        // below where it was?
        double thirtyAgo = close(bars.get(Math.max(0, bars.size() - 30)));
        return last < thirtyAgo * 0.95;
    }

    private boolean inUptrend(List<PriceHistory> bars, TechnicalAnalysisDto technical) {
        Double sma20 = value(technical.getSma20());
        Double sma50 = value(technical.getSma50());
        double last = close(bars.get(bars.size() - 1));
        return sma20 != null && sma50 != null && last > sma50 && sma20 > sma50;
    }

    /**
     * MACD histogram rising, or a bullish cross just printed.
     *
     * <p>The histogram is the distance between the MACD line and its signal,
     * so a rising histogram means downward momentum is weakening - which
     * happens before price turns, and is the earliest honest hint of a floor.
     */
    private boolean momentumTurningUp(TechnicalAnalysisDto technical) {
        if ("BULLISH_CROSS".equals(technical.getMacdCrossSignal())) {
            return true;
        }
        Double histogram = value(technical.getMacdHistogram());
        Double signal = value(technical.getMacdSignal());
        if (histogram == null || signal == null) {
            return false;
        }
        // Still below the signal line, but the gap has nearly closed - the
        // cross has not printed yet and is close.
        //
        // Compared as magnitudes on purpose. An earlier version asked whether
        // the MACD line had risen above `signal * 1.05`, which is wrong for
        // the negative values this branch deals in: scaling a negative number
        // up makes it smaller, so the test passed for any ordinary bearish
        // reading and every falling stock registered as "decelerating". The
        // histogram is the line minus the signal, so when it is negative the
        // line is below the signal by definition and no comparison between
        // the two can say anything else - only the size of the gap can.
        return histogram < 0 && Math.abs(histogram) < Math.abs(signal) * 0.2;
    }

    private boolean stochasticCrossingUp(TechnicalAnalysisDto technical) {
        Double k = value(technical.getStochasticK());
        Double d = value(technical.getStochasticD());
        return k != null && d != null && k > d && k < 40;
    }

    private boolean nearLowerBand(double last, TechnicalAnalysisDto technical) {
        Double lower = value(technical.getBollingerLower());
        return lower != null && lower > 0 && last <= lower * 1.02;
    }

    private boolean reclaimedShortAverage(double last, TechnicalAnalysisDto technical) {
        Double sma20 = value(technical.getSma20());
        return sma20 != null && last > sma20;
    }

    /**
     * The recent trough sits above the one before it.
     *
     * <p>A sequence of lower lows is the definition of a downtrend; the first
     * higher low is the earliest structural evidence that it has ended.
     */
    private boolean higherLow(List<PriceHistory> bars) {
        int size = bars.size();
        double recentLow = lowest(bars, Math.max(0, size - RECENT_WINDOW), size);
        double priorLow = lowest(bars, Math.max(0, size - PRIOR_WINDOW), Math.max(0, size - RECENT_WINDOW));
        return priorLow > 0 && recentLow > priorLow;
    }

    /**
     * Price made a lower low while RSI made a higher low.
     *
     * <p>The classic exhaustion tell: the last leg down carried less force
     * than the one before it, so sellers are running out even though the
     * price says otherwise. Approximated here by comparing the current RSI
     * against the level at the prior trough - the calculator gives one RSI
     * value rather than a series, so this is deliberately conservative and
     * only fires when price clearly made the lower low.
     */
    private boolean bullishDivergence(List<PriceHistory> bars, TechnicalAnalysisDto technical) {
        Double rsi = value(technical.getRsi14());
        if (rsi == null) {
            return false;
        }
        int size = bars.size();
        double recentLow = lowest(bars, Math.max(0, size - RECENT_WINDOW), size);
        double priorLow = lowest(bars, Math.max(0, size - PRIOR_WINDOW), Math.max(0, size - RECENT_WINDOW));

        // Price undercut the previous trough, yet momentum is no longer at an
        // extreme - the new low was made with less selling force.
        return priorLow > 0 && recentLow < priorLow && rsi > OVERSOLD_RSI && rsi < 55;
    }

    private double lowest(List<PriceHistory> bars, int from, int to) {
        double low = Double.MAX_VALUE;
        for (int i = from; i < to && i < bars.size(); i++) {
            low = Math.min(low, close(bars.get(i)));
        }
        return low == Double.MAX_VALUE ? 0 : low;
    }

    private List<PriceHistory> usable(List<PriceHistory> history) {
        List<PriceHistory> bars = new ArrayList<>();
        if (history == null) {
            return bars;
        }
        for (PriceHistory bar : history) {
            if (bar.getClose() != null && bar.getClose().signum() > 0 && bar.getTradeDate() != null) {
                bars.add(bar);
            }
        }
        return bars;
    }

    private double close(PriceHistory bar) {
        return bar.getClose().doubleValue();
    }

    private Double value(BigDecimal decimal) {
        return decimal == null ? null : decimal.doubleValue();
    }
}
