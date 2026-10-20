package com.tunindex.market_tool.collector.services.risk;

import com.tunindex.market_tool.collector.dto.risk.CorrelationMatrixDto;
import com.tunindex.market_tool.collector.dto.risk.CorrelationPairDto;
import com.tunindex.market_tool.collector.dto.risk.RiskMetricsDto;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.macro.MacroIndicatorsService;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Risk statistics computed from the daily closes we already store.
 *
 * <p>Two deliberate constraints run through this class:
 *
 * <ul>
 *   <li><b>Never invent a number.</b> Every figure has a minimum sample size,
 *       and below it the field comes back null. A null renders as "—"; a zero
 *       would render as a confident claim.
 *   <li><b>State the assumptions on the payload.</b> The risk-free rate, the
 *       trading-day count and the market proxy definition all ship in the
 *       response so the UI can show what the ratio was computed against
 *       instead of presenting a bare number as fact.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiskAnalyticsService {

    /** Below this many returns, dispersion statistics are noise, not signal. */
    private static final int MIN_OBSERVATIONS = 20;

    /** Beta needs a longer overlap than volatility before it means anything. */
    private static final int MIN_BETA_OBSERVATIONS = 40;

    /** Correlation cells below this overlap are left blank. */
    private static final int MIN_CORRELATION_OVERLAP = 30;

    /** BVMT trading days per year, used to annualise daily figures. */
    private static final int TRADING_DAYS_PER_YEAR = 252;

    private static final int MAX_CORRELATION_SYMBOLS = 30;
    private static final int TOP_PAIRS = 5;
    private static final int SCALE = 4;
    private static final int PCT_SCALE = 2;

    private final PriceHistoryRepository priceHistoryRepository;
    private final StockRepository stockRepository;
    private final MacroIndicatorsService macroIndicatorsService;

    /**
     * Fallback risk-free rate, used only when the central bank's published
     * policy rate is unavailable. Whatever rate ends up being used is echoed
     * back on the response so a ratio is never presented without the hurdle
     * it was measured against.
     */
    @Value("${market-tool.risk.risk-free-rate-pct:7.0}")
    private BigDecimal fallbackRiskFreeRatePct;

    /**
     * The hurdle for Sharpe and Sortino: Tunisia's actual policy rate when we
     * have read it from the central bank, the configured fallback otherwise.
     * Using a hardcoded constant here was quietly wrong — it was set to 8.0%
     * while the BCT's published rate is 7.0%, which biased every ratio.
     */
    private BigDecimal riskFreeRate() {
        return macroIndicatorsService.policyRatePct().orElse(fallbackRiskFreeRatePct);
    }

    @Transactional(readOnly = true)
    public RiskMetricsDto riskMetrics(String symbol, int windowDays) {
        String normalised = symbol.trim().toUpperCase();
        if (!stockRepository.findBySymbol(normalised).isPresent()) {
            throw new EntityNotFoundException(
                    "No stock found with symbol " + normalised,
                    ErrorCodes.STOCK_NOT_FOUND,
                    List.of("Symbol " + normalised + " is not tracked"));
        }

        LocalDate from = LocalDate.now().minusDays(clampWindow(windowDays));
        List<PriceHistory> history =
                priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(normalised, from);

        List<String> methodology = new ArrayList<>();
        methodology.add("Computed from " + history.size() + " stored daily closes for " + normalised
                + " from " + from + " onwards.");
        methodology.add("Daily returns are simple close-to-close changes; annualisation multiplies by "
                + "the square root of " + TRADING_DAYS_PER_YEAR + " trading days.");

        if (history.size() < MIN_OBSERVATIONS + 1) {
            methodology.add("Fewer than " + (MIN_OBSERVATIONS + 1) + " closes are available, so no risk figure "
                    + "is reported — a dispersion statistic on this few points would be noise.");
            return RiskMetricsDto.builder()
                    .symbol(normalised)
                    .observations(Math.max(history.size() - 1, 0))
                    .periodStart(history.isEmpty() ? null : history.get(0).getTradeDate())
                    .periodEnd(history.isEmpty() ? null : history.get(history.size() - 1).getTradeDate())
                    .riskFreeRatePct(riskFreeRate())
                    .methodology(methodology)
                    .build();
        }

        List<Double> returns = dailyReturns(history);
        List<Double> closes = history.stream()
                .map(point -> point.getClose().doubleValue())
                .toList();

        double volatility = standardDeviation(returns) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100;
        double downside = downsideDeviation(returns) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100;

        Drawdown drawdown = maxDrawdown(history);

        double periodReturn = (closes.get(closes.size() - 1) / closes.get(0) - 1) * 100;
        // Scale the observed return to a year by trading days, not calendar
        // days: a 60-session window is 60/252 of a year regardless of how many
        // weekends and holidays fell inside it.
        double annualisedReturn = periodReturn * ((double) TRADING_DAYS_PER_YEAR / returns.size());

        BetaResult beta = betaAgainstMarket(normalised, history);

        BigDecimal riskFree = riskFreeRate();
        Double sharpe = ratio(annualisedReturn, volatility, riskFree);
        Double sortino = ratio(annualisedReturn, downside, riskFree);

        List<Double> sorted = new ArrayList<>(returns);
        Collections.sort(sorted);
        double var95 = percentile(sorted, 0.05) * 100;
        double cvar95 = conditionalTail(sorted, 0.05) * 100;

        long positiveDays = returns.stream().filter(value -> value > 0).count();

        methodology.add("Beta is measured against an equal-weighted index of every BVMT name we hold "
                + "history for — the exchange publishes TUNINDEX itself, but we do not collect its levels, "
                + "so this proxy is what the number actually describes.");
        methodology.add("Sharpe and Sortino use a " + riskFree + "% annual risk-free rate"
                + (macroIndicatorsService.policyRatePct().isPresent()
                        ? " — the policy rate published by the Banque Centrale de Tunisie."
                        : ", the configured fallback (the central bank's published rate was unavailable)."));
        methodology.add("Value at risk is the historical 5th percentile of daily returns over this window, "
                + "not a modelled or normal-distribution estimate.");
        if (beta.observations < MIN_BETA_OBSERVATIONS) {
            methodology.add("Beta is omitted: only " + beta.observations + " days overlap with the market "
                    + "series, below the " + MIN_BETA_OBSERVATIONS + " required.");
        }

        return RiskMetricsDto.builder()
                .symbol(normalised)
                .observations(returns.size())
                .periodStart(history.get(0).getTradeDate())
                .periodEnd(history.get(history.size() - 1).getTradeDate())
                .annualisedVolatilityPct(round(volatility, PCT_SCALE))
                .downsideDeviationPct(round(downside, PCT_SCALE))
                .maxDrawdownPct(round(drawdown.magnitude, PCT_SCALE))
                .maxDrawdownPeak(drawdown.peakDate)
                .maxDrawdownTrough(drawdown.troughDate)
                .periodReturnPct(round(periodReturn, PCT_SCALE))
                .annualisedReturnPct(round(annualisedReturn, PCT_SCALE))
                .beta(beta.observations >= MIN_BETA_OBSERVATIONS ? round(beta.beta, SCALE) : null)
                .varianceExplained(beta.observations >= MIN_BETA_OBSERVATIONS ? round(beta.rSquared, SCALE) : null)
                .sharpeRatio(sharpe == null ? null : round(sharpe, SCALE))
                .sortinoRatio(sortino == null ? null : round(sortino, SCALE))
                .riskFreeRatePct(riskFree)
                .valueAtRisk95Pct(round(var95, PCT_SCALE))
                .conditionalVar95Pct(round(cvar95, PCT_SCALE))
                .bestDayPct(round(Collections.max(returns) * 100, PCT_SCALE))
                .worstDayPct(round(Collections.min(returns) * 100, PCT_SCALE))
                .positiveDaysPct(round(positiveDays * 100.0 / returns.size(), PCT_SCALE))
                .methodology(methodology)
                .build();
    }

    /**
     * Pearson correlation of daily returns for every pair in the set.
     *
     * <p>Returns are aligned by trade date, not by position: two names with
     * different suspension histories have different row counts, and zipping
     * them positionally would correlate Monday against Wednesday and produce a
     * confident, meaningless number.
     */
    @Transactional(readOnly = true)
    public CorrelationMatrixDto correlationMatrix(List<String> symbols, int windowDays) {
        List<String> normalised = symbols.stream()
                .map(symbol -> symbol.trim().toUpperCase())
                .filter(symbol -> !symbol.isEmpty())
                .distinct()
                .toList();

        if (normalised.size() < 2) {
            throw new InvalidEntityException(
                    "At least two symbols are required to correlate",
                    ErrorCodes.INVALID_PARAMETER,
                    List.of("Pass symbols as a comma-separated list, e.g. symbols=AB,BIAT,SFBT"));
        }
        if (normalised.size() > MAX_CORRELATION_SYMBOLS) {
            throw new InvalidEntityException(
                    "At most " + MAX_CORRELATION_SYMBOLS + " symbols can be correlated at once",
                    ErrorCodes.INVALID_PARAMETER,
                    List.of("Received " + normalised.size() + " symbols"));
        }

        int window = clampWindow(windowDays);
        LocalDate from = LocalDate.now().minusDays(window);

        Map<String, Map<LocalDate, Double>> returnsBySymbol = new LinkedHashMap<>();
        for (String symbol : normalised) {
            List<PriceHistory> history =
                    priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(symbol, from);
            returnsBySymbol.put(symbol, datedReturns(history));
        }

        List<List<BigDecimal>> matrix = new ArrayList<>();
        List<List<Integer>> overlaps = new ArrayList<>();
        List<CorrelationPairDto> pairs = new ArrayList<>();

        for (int i = 0; i < normalised.size(); i++) {
            List<BigDecimal> row = new ArrayList<>();
            List<Integer> overlapRow = new ArrayList<>();
            Map<LocalDate, Double> left = returnsBySymbol.get(normalised.get(i));

            for (int j = 0; j < normalised.size(); j++) {
                if (i == j) {
                    row.add(BigDecimal.ONE);
                    overlapRow.add(left.size());
                    continue;
                }

                Map<LocalDate, Double> right = returnsBySymbol.get(normalised.get(j));
                Aligned aligned = align(left, right);
                overlapRow.add(aligned.size());

                if (aligned.size() < MIN_CORRELATION_OVERLAP) {
                    row.add(null);
                    continue;
                }

                Double correlation = pearson(aligned.left, aligned.right);
                if (correlation == null) {
                    row.add(null);
                    continue;
                }

                BigDecimal rounded = round(correlation, SCALE);
                row.add(rounded);
                // Each unordered pair is recorded once, from the upper triangle.
                if (j > i) {
                    pairs.add(CorrelationPairDto.builder()
                            .symbolA(normalised.get(i))
                            .symbolB(normalised.get(j))
                            .correlation(rounded)
                            .overlap(aligned.size())
                            .build());
                }
            }
            matrix.add(row);
            overlaps.add(overlapRow);
        }

        List<CorrelationPairDto> ascending = pairs.stream()
                .sorted(Comparator.comparing(CorrelationPairDto::getCorrelation))
                .toList();
        List<CorrelationPairDto> descending = pairs.stream()
                .sorted(Comparator.comparing(CorrelationPairDto::getCorrelation).reversed())
                .toList();

        return CorrelationMatrixDto.builder()
                .symbols(normalised)
                .matrix(matrix)
                .overlap(overlaps)
                .windowDays(window)
                .minOverlap(MIN_CORRELATION_OVERLAP)
                .mostDiversifying(ascending.stream().limit(TOP_PAIRS).toList())
                .mostRedundant(descending.stream().limit(TOP_PAIRS).toList())
                .build();
    }

    // ── internals ────────────────────────────────────────────────────────────

    private int clampWindow(int windowDays) {
        return Math.min(Math.max(windowDays, 30), 1825);
    }

    private List<Double> dailyReturns(List<PriceHistory> history) {
        List<Double> returns = new ArrayList<>(history.size());
        for (int i = 1; i < history.size(); i++) {
            double previous = history.get(i - 1).getClose().doubleValue();
            if (previous == 0) {
                continue;
            }
            returns.add(history.get(i).getClose().doubleValue() / previous - 1);
        }
        return returns;
    }

    /** Same returns, keyed by the date they were realised on, for alignment. */
    private Map<LocalDate, Double> datedReturns(List<PriceHistory> history) {
        Map<LocalDate, Double> returns = new TreeMap<>();
        for (int i = 1; i < history.size(); i++) {
            double previous = history.get(i - 1).getClose().doubleValue();
            if (previous == 0) {
                continue;
            }
            returns.put(history.get(i).getTradeDate(),
                    history.get(i).getClose().doubleValue() / previous - 1);
        }
        return returns;
    }

    private Aligned align(Map<LocalDate, Double> left, Map<LocalDate, Double> right) {
        List<Double> leftValues = new ArrayList<>();
        List<Double> rightValues = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> entry : left.entrySet()) {
            Double other = right.get(entry.getKey());
            if (other != null) {
                leftValues.add(entry.getValue());
                rightValues.add(other);
            }
        }
        return new Aligned(leftValues, rightValues);
    }

    /**
     * Regresses the name against an equal-weighted market return series built
     * from every symbol we hold history for. Equal weighting is a choice
     * forced by the data: we have no reliable free-float market cap for most
     * BVMT names, and a cap-weighted proxy on partial caps would be less
     * honest than an average of the moves we can actually observe.
     */
    private BetaResult betaAgainstMarket(String symbol, List<PriceHistory> history) {
        Map<LocalDate, Double> market = marketReturns(history.get(0).getTradeDate());
        Aligned aligned = align(datedReturns(history), market);

        if (aligned.size() < MIN_BETA_OBSERVATIONS) {
            return new BetaResult(0, 0, aligned.size());
        }

        double marketVariance = variance(aligned.right);
        if (marketVariance == 0) {
            return new BetaResult(0, 0, aligned.size());
        }

        double beta = covariance(aligned.left, aligned.right) / marketVariance;
        Double correlation = pearson(aligned.left, aligned.right);
        double rSquared = correlation == null ? 0 : correlation * correlation;
        return new BetaResult(beta, rSquared, aligned.size());
    }

    /**
     * Cross-sectional mean return per trade date across all symbols. Built
     * fresh on each call: it is one indexed query plus a grouping pass, and a
     * cached copy would silently go stale the moment the collector writes a
     * new close.
     */
    private Map<LocalDate, Double> marketReturns(LocalDate from) {
        List<String> allSymbols = stockRepository.findAll().stream()
                .map(stock -> stock.getSymbol())
                .toList();

        List<PriceHistory> all = priceHistoryRepository
                .findBySymbolInAndTradeDateGreaterThanEqualOrderBySymbolAscTradeDateAsc(allSymbols, from);

        Map<String, List<PriceHistory>> bySymbol = new LinkedHashMap<>();
        for (PriceHistory point : all) {
            bySymbol.computeIfAbsent(point.getSymbol(), key -> new ArrayList<>()).add(point);
        }

        Map<LocalDate, double[]> sums = new TreeMap<>();
        for (List<PriceHistory> series : bySymbol.values()) {
            for (Map.Entry<LocalDate, Double> entry : datedReturns(series).entrySet()) {
                double[] accumulator = sums.computeIfAbsent(entry.getKey(), key -> new double[2]);
                accumulator[0] += entry.getValue();
                accumulator[1]++;
            }
        }

        Map<LocalDate, Double> market = new TreeMap<>();
        sums.forEach((date, accumulator) -> market.put(date, accumulator[0] / accumulator[1]));
        return market;
    }

    private Drawdown maxDrawdown(List<PriceHistory> history) {
        double peak = history.get(0).getClose().doubleValue();
        LocalDate peakDate = history.get(0).getTradeDate();
        LocalDate runningPeakDate = peakDate;
        double worst = 0;
        LocalDate troughDate = peakDate;

        for (PriceHistory point : history) {
            double close = point.getClose().doubleValue();
            if (close > peak) {
                peak = close;
                runningPeakDate = point.getTradeDate();
            }
            if (peak > 0) {
                double drawdown = (close / peak - 1) * 100;
                if (drawdown < worst) {
                    worst = drawdown;
                    troughDate = point.getTradeDate();
                    peakDate = runningPeakDate;
                }
            }
        }
        return new Drawdown(worst, peakDate, troughDate);
    }

    private double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    /** Sample standard deviation (n-1) — these are samples, not populations. */
    private double standardDeviation(List<Double> values) {
        return Math.sqrt(variance(values));
    }

    private double variance(List<Double> values) {
        if (values.size() < 2) {
            return 0;
        }
        double average = mean(values);
        double sum = 0;
        for (double value : values) {
            sum += (value - average) * (value - average);
        }
        return sum / (values.size() - 1);
    }

    private double covariance(List<Double> left, List<Double> right) {
        if (left.size() < 2) {
            return 0;
        }
        double leftMean = mean(left);
        double rightMean = mean(right);
        double sum = 0;
        for (int i = 0; i < left.size(); i++) {
            sum += (left.get(i) - leftMean) * (right.get(i) - rightMean);
        }
        return sum / (left.size() - 1);
    }

    private Double pearson(List<Double> left, List<Double> right) {
        double leftSd = standardDeviation(left);
        double rightSd = standardDeviation(right);
        // A series that never moved has no correlation with anything; null
        // says that, where 0 would claim independence we cannot demonstrate.
        if (leftSd == 0 || rightSd == 0) {
            return null;
        }
        return covariance(left, right) / (leftSd * rightSd);
    }

    /**
     * Dispersion of losing days only. Divided by the full sample size, not the
     * count of negative days: that is the standard Sortino convention, and it
     * stops a name that fell twice out of 200 sessions from looking violently
     * risky on the downside.
     */
    private double downsideDeviation(List<Double> returns) {
        double sum = 0;
        for (double value : returns) {
            if (value < 0) {
                sum += value * value;
            }
        }
        return Math.sqrt(sum / returns.size());
    }

    private Double ratio(double annualisedReturn, double denominator, BigDecimal riskFree) {
        if (denominator == 0) {
            return null;
        }
        return (annualisedReturn - riskFree.doubleValue()) / denominator;
    }

    /** Nearest-rank percentile over an already-sorted ascending list. */
    private double percentile(List<Double> sorted, double fraction) {
        int index = (int) Math.floor(fraction * sorted.size());
        return sorted.get(Math.min(Math.max(index, 0), sorted.size() - 1));
    }

    private double conditionalTail(List<Double> sorted, double fraction) {
        int cutoff = Math.max((int) Math.floor(fraction * sorted.size()), 1);
        double sum = 0;
        for (int i = 0; i < cutoff; i++) {
            sum += sorted.get(i);
        }
        return sum / cutoff;
    }

    private BigDecimal round(double value, int scale) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    private record Aligned(List<Double> left, List<Double> right) {
        int size() {
            return left.size();
        }
    }

    private record BetaResult(double beta, double rSquared, int observations) {
    }

    private record Drawdown(double magnitude, LocalDate peakDate, LocalDate troughDate) {
    }
}
