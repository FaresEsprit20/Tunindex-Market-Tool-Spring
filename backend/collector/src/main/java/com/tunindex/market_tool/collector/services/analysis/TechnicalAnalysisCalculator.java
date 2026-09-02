package com.tunindex.market_tool.collector.services.analysis;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public TechnicalAnalysisDto compute(List<PriceHistory> historyAscending) {
        double[] closes = historyAscending.stream()
                .filter(p -> p.getClose() != null)
                .mapToDouble(p -> p.getClose().doubleValue())
                .toArray();

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
