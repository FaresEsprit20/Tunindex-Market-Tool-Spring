package com.tunindex.market_tool.collector.services.analysis;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard technical-indicator formulas applied to real, scraped daily
 * closes (PriceHistory) — SMA/EMA/RSI/MACD/Bollinger are all computed here
 * from scratch, not read from a third party's own pre-computed values.
 * Input must be sorted ascending by trade date.
 */
@Component
public class TechnicalAnalysisCalculator {

    private static final int SMA_SHORT = 20;
    private static final int SMA_LONG = 50;
    private static final int RSI_PERIOD = 14;
    private static final int MACD_FAST = 12;
    private static final int MACD_SLOW = 26;
    private static final int MACD_SIGNAL = 9;
    private static final int BOLLINGER_PERIOD = 20;
    private static final double BOLLINGER_STD_MULTIPLIER = 2.0;
    private static final int TRADING_DAYS_PER_YEAR = 252;
    private static final int STOCHASTIC_PERIOD = 14;
    private static final int STOCHASTIC_SMOOTHING = 3;
    private static final int WILLIAMS_R_PERIOD = 14;
    private static final int ATR_PERIOD = 14;
    private static final int ADX_PERIOD = 14;

    public TechnicalAnalysisDto compute(List<PriceHistory> historyAscending) {
        // Stochastic/Williams %R/ATR/ADX all need the full OHLC bar, not
        // just the close — filtered together so every array below stays
        // index-aligned to the same set of real trading days.
        List<PriceHistory> bars = historyAscending.stream()
                .filter(p -> p.getClose() != null && p.getHigh() != null && p.getLow() != null)
                .toList();

        double[] closes = bars.stream().mapToDouble(p -> p.getClose().doubleValue()).toArray();
        double[] highs = bars.stream().mapToDouble(p -> p.getHigh().doubleValue()).toArray();
        double[] lows = bars.stream().mapToDouble(p -> p.getLow().doubleValue()).toArray();

        TechnicalAnalysisDto.TechnicalAnalysisDtoBuilder builder = TechnicalAnalysisDto.builder()
                .dataPointsUsed(closes.length);

        if (closes.length == 0) {
            return builder.build();
        }

        double lastClose = closes[closes.length - 1];
        builder.lastClose(round(lastClose));

        Double sma20 = sma(closes, SMA_SHORT);
        Double sma50 = sma(closes, SMA_LONG);
        builder.sma20(round(sma20));
        builder.sma50(round(sma50));
        builder.trendSignal(trendSignal(lastClose, sma20, sma50));

        Double rsi = rsi(closes, RSI_PERIOD);
        builder.rsi14(round(rsi));
        builder.rsiSignal(rsiSignal(rsi));

        double[] macdLine = macdLineSeries(closes);
        double[] signalLine = macdLine.length > 0 ? ema(macdLine, MACD_SIGNAL) : new double[0];
        if (macdLine.length > 0 && signalLine.length > 0) {
            double macd = macdLine[macdLine.length - 1];
            double signal = signalLine[signalLine.length - 1];
            double histogram = macd - signal;
            builder.macdLine(round(macd));
            builder.macdSignal(round(signal));
            builder.macdHistogram(round(histogram));
            builder.macdCrossSignal(macdCrossSignal(macdLine, signalLine));
        }

        double[] bollinger = bollingerBands(closes, BOLLINGER_PERIOD, BOLLINGER_STD_MULTIPLIER);
        if (bollinger != null) {
            builder.bollingerUpper(round(bollinger[0]));
            builder.bollingerMiddle(round(bollinger[1]));
            builder.bollingerLower(round(bollinger[2]));
        }

        Double volatility = annualizedVolatilityPct(closes);
        builder.volatilityAnnualizedPct(round(volatility));

        double[] stochastic = stochastic(highs, lows, closes, STOCHASTIC_PERIOD, STOCHASTIC_SMOOTHING);
        if (stochastic != null) {
            builder.stochasticK(round(stochastic[0]));
            builder.stochasticD(round(stochastic[1]));
            builder.stochasticSignal(stochasticSignal(stochastic[0]));
        }

        Double williamsR = williamsR(highs, lows, closes, WILLIAMS_R_PERIOD);
        builder.williamsR(round(williamsR));
        builder.williamsRSignal(williamsRSignal(williamsR));

        Double atr = atr(highs, lows, closes, ATR_PERIOD);
        builder.atr14(round(atr));

        Double adx = adx(highs, lows, closes, ADX_PERIOD);
        builder.adx14(round(adx));
        builder.adxSignal(adxSignal(adx));

        return builder.build();
    }

    private Double sma(double[] closes, int period) {
        if (closes.length < period) return null;
        double sum = 0;
        for (int i = closes.length - period; i < closes.length; i++) {
            sum += closes[i];
        }
        return sum / period;
    }

    /** Standard population EMA series, seeded with the SMA of the first `period` values. */
    private double[] ema(double[] values, int period) {
        if (values.length < period) return new double[0];
        double[] result = new double[values.length - period + 1];
        double sum = 0;
        for (int i = 0; i < period; i++) sum += values[i];
        double prevEma = sum / period;
        result[0] = prevEma;

        double multiplier = 2.0 / (period + 1);
        int resultIdx = 1;
        for (int i = period; i < values.length; i++) {
            prevEma = (values[i] - prevEma) * multiplier + prevEma;
            result[resultIdx++] = prevEma;
        }
        return result;
    }

    private double[] macdLineSeries(double[] closes) {
        double[] emaFast = ema(closes, MACD_FAST);
        double[] emaSlow = ema(closes, MACD_SLOW);
        if (emaFast.length == 0 || emaSlow.length == 0) return new double[0];

        // emaFast is longer (shorter period => starts earlier); align both series to their common tail.
        int offset = emaFast.length - emaSlow.length;
        double[] macd = new double[emaSlow.length];
        for (int i = 0; i < emaSlow.length; i++) {
            macd[i] = emaFast[i + offset] - emaSlow[i];
        }
        return macd;
    }

    private String macdCrossSignal(double[] macdLine, double[] signalLine) {
        int offset = macdLine.length - signalLine.length;
        if (signalLine.length < 2) return "NONE";

        double currMacd = macdLine[macdLine.length - 1];
        double currSignal = signalLine[signalLine.length - 1];
        double prevMacd = macdLine[macdLine.length - 2];
        double prevSignal = signalLine[signalLine.length - 2];

        double currHist = currMacd - currSignal;
        double prevHist = prevMacd - prevSignal;

        if (prevHist <= 0 && currHist > 0) return "BULLISH_CROSS";
        if (prevHist >= 0 && currHist < 0) return "BEARISH_CROSS";
        return "NONE";
    }

    /** Wilder's smoothing method, the standard RSI formula. */
    private Double rsi(double[] closes, int period) {
        if (closes.length < period + 1) return null;

        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) avgGain += change;
            else avgLoss += -change;
        }
        avgGain /= period;
        avgLoss /= period;

        for (int i = period + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            double gain = Math.max(change, 0);
            double loss = Math.max(-change, 0);
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
        }

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /** Returns [upper, middle, lower] or null if not enough data. */
    private double[] bollingerBands(double[] closes, int period, double stdMultiplier) {
        if (closes.length < period) return null;
        double mean = sma(closes, period);

        double sumSquaredDiff = 0;
        for (int i = closes.length - period; i < closes.length; i++) {
            double diff = closes[i] - mean;
            sumSquaredDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSquaredDiff / period);

        return new double[]{mean + stdMultiplier * stdDev, mean, mean - stdMultiplier * stdDev};
    }

    /** Annualized volatility from daily log returns' standard deviation. */
    private Double annualizedVolatilityPct(double[] closes) {
        if (closes.length < 2) return null;
        double[] returns = new double[closes.length - 1];
        for (int i = 1; i < closes.length; i++) {
            returns[i - 1] = Math.log(closes[i] / closes[i - 1]);
        }
        double mean = 0;
        for (double r : returns) mean += r;
        mean /= returns.length;

        double sumSquaredDiff = 0;
        for (double r : returns) sumSquaredDiff += (r - mean) * (r - mean);
        double dailyStdDev = Math.sqrt(sumSquaredDiff / returns.length);

        return dailyStdDev * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100;
    }

    /** Standard (14,3,3) stochastic oscillator: %K over `period`, %D = SMA(%K, smoothing). */
    private double[] stochastic(double[] highs, double[] lows, double[] closes, int period, int smoothing) {
        if (closes.length < period + smoothing - 1) return null;

        double[] kValues = new double[closes.length - period + 1];
        for (int i = period - 1; i < closes.length; i++) {
            double highestHigh = Double.NEGATIVE_INFINITY;
            double lowestLow = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                highestHigh = Math.max(highestHigh, highs[j]);
                lowestLow = Math.min(lowestLow, lows[j]);
            }
            double range = highestHigh - lowestLow;
            kValues[i - period + 1] = range == 0 ? 50.0 : (closes[i] - lowestLow) / range * 100.0;
        }

        if (kValues.length < smoothing) return null;
        double dSum = 0;
        for (int i = kValues.length - smoothing; i < kValues.length; i++) dSum += kValues[i];

        return new double[]{kValues[kValues.length - 1], dSum / smoothing};
    }

    private String stochasticSignal(double k) {
        if (k >= 80) return "OVERBOUGHT";
        if (k <= 20) return "OVERSOLD";
        return "NEUTRAL";
    }

    /** Williams %R: like stochastic but scaled -100..0, with the poles flipped. */
    private Double williamsR(double[] highs, double[] lows, double[] closes, int period) {
        if (closes.length < period) return null;
        double highestHigh = Double.NEGATIVE_INFINITY;
        double lowestLow = Double.POSITIVE_INFINITY;
        for (int i = closes.length - period; i < closes.length; i++) {
            highestHigh = Math.max(highestHigh, highs[i]);
            lowestLow = Math.min(lowestLow, lows[i]);
        }
        double range = highestHigh - lowestLow;
        if (range == 0) return -50.0;
        return (highestHigh - closes[closes.length - 1]) / range * -100.0;
    }

    private String williamsRSignal(Double r) {
        if (r == null) return "NEUTRAL";
        if (r >= -20) return "OVERBOUGHT";
        if (r <= -80) return "OVERSOLD";
        return "NEUTRAL";
    }

    /** Wilder-smoothed Average True Range — absolute volatility in price units, not %. */
    private Double atr(double[] highs, double[] lows, double[] closes, int period) {
        if (closes.length < period + 1) return null;

        double[] trueRanges = trueRanges(highs, lows, closes);

        double atr = 0;
        for (int i = 0; i < period; i++) atr += trueRanges[i];
        atr /= period;

        for (int i = period; i < trueRanges.length; i++) {
            atr = (atr * (period - 1) + trueRanges[i]) / period;
        }
        return atr;
    }

    private double[] trueRanges(double[] highs, double[] lows, double[] closes) {
        double[] trueRanges = new double[closes.length - 1];
        for (int i = 1; i < closes.length; i++) {
            double highLow = highs[i] - lows[i];
            double highPrevClose = Math.abs(highs[i] - closes[i - 1]);
            double lowPrevClose = Math.abs(lows[i] - closes[i - 1]);
            trueRanges[i - 1] = Math.max(highLow, Math.max(highPrevClose, lowPrevClose));
        }
        return trueRanges;
    }

    /**
     * Wilder's Average Directional Index — trend STRENGTH regardless of
     * direction (a strong downtrend scores just as high as a strong
     * uptrend; pair with trendSignal/MACD for direction).
     */
    private Double adx(double[] highs, double[] lows, double[] closes, int period) {
        int n = closes.length;
        if (n < period * 2) return null;

        double[] plusDm = new double[n - 1];
        double[] minusDm = new double[n - 1];
        for (int i = 1; i < n; i++) {
            double upMove = highs[i] - highs[i - 1];
            double downMove = lows[i - 1] - lows[i];
            plusDm[i - 1] = (upMove > downMove && upMove > 0) ? upMove : 0;
            minusDm[i - 1] = (downMove > upMove && downMove > 0) ? downMove : 0;
        }
        double[] tr = trueRanges(highs, lows, closes);

        double smoothedPlusDm = sumFirst(plusDm, period);
        double smoothedMinusDm = sumFirst(minusDm, period);
        double smoothedTr = sumFirst(tr, period);

        List<Double> dxValues = new ArrayList<>();
        dxValues.add(directionalIndex(smoothedPlusDm, smoothedMinusDm, smoothedTr));

        for (int i = period; i < plusDm.length; i++) {
            smoothedPlusDm = smoothedPlusDm - (smoothedPlusDm / period) + plusDm[i];
            smoothedMinusDm = smoothedMinusDm - (smoothedMinusDm / period) + minusDm[i];
            smoothedTr = smoothedTr - (smoothedTr / period) + tr[i];
            dxValues.add(directionalIndex(smoothedPlusDm, smoothedMinusDm, smoothedTr));
        }

        if (dxValues.size() < period) return null;

        double adx = 0;
        for (int i = 0; i < period; i++) adx += dxValues.get(i);
        adx /= period;

        for (int i = period; i < dxValues.size(); i++) {
            adx = (adx * (period - 1) + dxValues.get(i)) / period;
        }
        return adx;
    }

    private double directionalIndex(double smoothedPlusDm, double smoothedMinusDm, double smoothedTr) {
        double plusDi = smoothedTr == 0 ? 0 : 100 * smoothedPlusDm / smoothedTr;
        double minusDi = smoothedTr == 0 ? 0 : 100 * smoothedMinusDm / smoothedTr;
        double sum = plusDi + minusDi;
        return sum == 0 ? 0 : 100 * Math.abs(plusDi - minusDi) / sum;
    }

    private double sumFirst(double[] values, int count) {
        double sum = 0;
        for (int i = 0; i < count; i++) sum += values[i];
        return sum;
    }

    private String adxSignal(Double adx) {
        if (adx == null) return "NO_TREND";
        if (adx >= 25) return "STRONG_TREND";
        if (adx >= 15) return "WEAK_TREND";
        return "NO_TREND";
    }

    private String trendSignal(double lastClose, Double sma20, Double sma50) {
        if (sma20 == null || sma50 == null) return "NEUTRAL";
        if (lastClose > sma20 && sma20 > sma50) return "BULLISH";
        if (lastClose < sma20 && sma20 < sma50) return "BEARISH";
        return "NEUTRAL";
    }

    private String rsiSignal(Double rsi) {
        if (rsi == null) return "NEUTRAL";
        if (rsi >= 70) return "OVERBOUGHT";
        if (rsi <= 30) return "OVERSOLD";
        return "NEUTRAL";
    }

    private BigDecimal round(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return null;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
