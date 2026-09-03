package com.tunindex.market_tool.collector.services.backtest;

import com.tunindex.market_tool.collector.dto.analysis.TechnicalAnalysisDto;
import com.tunindex.market_tool.collector.dto.backtest.BacktestResultDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.collector.services.analysis.TechnicalAnalysisCalculator;
import com.tunindex.market_tool.collector.services.scoring.TunindexScorer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Replays the scorer's <em>timing</em> component over stored price history
 * and measures what actually followed.
 *
 * <h2>Why timing only</h2>
 * Fundamentals are stored as current values with no history — there is no
 * record of what a stock's P/E or margin of safety was last March. Scoring
 * a past date with today's fundamentals would leak information that did not
 * exist then, and every band would look predictive for reasons that have
 * nothing to do with the score. The timing component is the part that can
 * be honestly reconstructed: it reads only price history, which is dated.
 *
 * <h2>What one observation is</h2>
 * For a symbol, at an evaluation date D: score the timing component using
 * only closes up to and including D, then measure the return from D's close
 * to the close nearest D + horizon. Evaluation dates step forward by
 * {@code stepDays} so consecutive observations do not measure overlapping
 * windows of the same move.
 *
 * <h2>Reading the output</h2>
 * A band's win rate means nothing alone — it has to beat the baseline win
 * rate across every observation, which is why {@code edgeOverBaseline} is
 * computed here rather than left to the reader.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestServiceImpl implements BacktestService {

    /** Trading days of history required before a date can be scored at all. */
    private static final int MIN_HISTORY_FOR_SCORING = 60;

    /** How far a forward-return lookup may drift from the exact target date. */
    private static final int FORWARD_TOLERANCE_DAYS = 7;

    private static final int[][] BANDS = {
            {0, 39}, {40, 54}, {55, 69}, {70, 84}, {85, 100}
    };
    private static final String[] BAND_LABELS = {
            "0–39", "40–54", "55–69", "70–84", "85–100"
    };

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TechnicalAnalysisCalculator technicalAnalysisCalculator;
    private final TunindexScorer scorer;

    private record Observation(int score, BigDecimal forwardReturnPct) {}

    @Override
    @Transactional(readOnly = true)
    public BacktestResultDto run(int horizonDays, int stepDays) {
        List<String> symbols = stockRepository.findAll().stream().map(s -> s.getSymbol()).toList();
        List<Observation> observations = new ArrayList<>();
        int symbolsTested = 0;

        log.info("🔁 Backtest starting: horizon={}d, step={}d, {} symbols", horizonDays, stepDays, symbols.size());

        for (String symbol : symbols) {
            List<PriceHistory> history = priceHistoryRepository
                    .findBySymbolOrderByTradeDateAsc(symbol)
                    .stream()
                    .filter(p -> p.getClose() != null)
                    .toList();

            if (history.size() < MIN_HISTORY_FOR_SCORING + 5) {
                continue;
            }
            symbolsTested++;

            for (int i = MIN_HISTORY_FOR_SCORING; i < history.size(); i += stepDays) {
                PriceHistory at = history.get(i);
                // Only what was knowable on the evaluation date.
                List<PriceHistory> upToDate = history.subList(0, i + 1);

                Integer timingScore = scoreAsOf(upToDate);
                if (timingScore == null) {
                    continue;
                }

                BigDecimal forward = forwardReturnPct(history, i, at.getTradeDate().plusDays(horizonDays));
                if (forward == null) {
                    continue;
                }
                observations.add(new Observation(timingScore, forward));
            }
        }

        log.info("🔁 Backtest complete: {} observations across {} symbols", observations.size(), symbolsTested);
        return summarise(observations, horizonDays, stepDays, symbolsTested);
    }

    /**
     * The timing score as of the last bar in the supplied window, computed
     * through the production scorer rather than a local copy.
     */
    private Integer scoreAsOf(List<PriceHistory> upToDate) {
        TechnicalAnalysisDto technical;
        try {
            technical = technicalAnalysisCalculator.compute(upToDate);
        } catch (Exception e) {
            return null;
        }
        if (technical == null) {
            return null;
        }

        BigDecimal positionInRange = positionIn52WeekRange(upToDate);
        // reasons/warnings are collected and discarded: the backtest wants
        // the number, not the prose the UI shows beside it.
        return scorer.scoreTimingFrom(positionInRange, technical, new ArrayList<>(), new ArrayList<>());
    }

    /**
     * Where the latest close sits in its trailing 52-week range, on the same
     * scale the live scorer uses: 100 = at the low, 0 = at the high. Computed
     * from the window rather than read off the stock, which carries only a
     * current value.
     */
    private BigDecimal positionIn52WeekRange(List<PriceHistory> upToDate) {
        LocalDate last = upToDate.get(upToDate.size() - 1).getTradeDate();
        LocalDate yearAgo = last.minusDays(365);

        BigDecimal high = null;
        BigDecimal low = null;
        for (PriceHistory point : upToDate) {
            if (point.getTradeDate().isBefore(yearAgo)) {
                continue;
            }
            BigDecimal close = point.getClose();
            if (high == null || close.compareTo(high) > 0) high = close;
            if (low == null || close.compareTo(low) < 0) low = close;
        }
        if (high == null || low == null) {
            return null;
        }

        BigDecimal range = high.subtract(low);
        if (range.signum() == 0) {
            return BigDecimal.valueOf(50);
        }
        BigDecimal current = upToDate.get(upToDate.size() - 1).getClose();
        // Distance down from the high, as a percentage of the range.
        return high.subtract(current)
                .divide(range, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Return from the close at {@code fromIndex} to the close nearest the
     * target date. Returns null when history ends before the horizon — the
     * alternative, measuring to the last available bar, would quietly
     * shorten the horizon for recent observations and bias the result.
     */
    private BigDecimal forwardReturnPct(List<PriceHistory> history, int fromIndex, LocalDate target) {
        BigDecimal entry = history.get(fromIndex).getClose();
        if (entry == null || entry.signum() == 0) {
            return null;
        }

        PriceHistory best = null;
        for (int j = fromIndex + 1; j < history.size(); j++) {
            PriceHistory candidate = history.get(j);
            if (candidate.getTradeDate().isBefore(target)) {
                best = candidate;
                continue;
            }
            best = candidate;
            break;
        }

        if (best == null) {
            return null;
        }
        long drift = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(best.getTradeDate(), target));
        if (drift > FORWARD_TOLERANCE_DAYS) {
            return null;
        }

        return best.getClose().subtract(entry)
                .divide(entry, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BacktestResultDto summarise(List<Observation> observations, int horizonDays,
                                        int stepDays, int symbolsTested) {
        BigDecimal baselineWin = winRate(observations);
        BigDecimal baselineAvg = avgReturn(observations);

        List<BacktestResultDto.BandResultDto> bands = new ArrayList<>();
        for (int b = 0; b < BANDS.length; b++) {
            final int min = BANDS[b][0];
            final int max = BANDS[b][1];
            List<Observation> inBand = observations.stream()
                    .filter(o -> o.score() >= min && o.score() <= max)
                    .toList();

            BigDecimal bandWin = winRate(inBand);
            bands.add(BacktestResultDto.BandResultDto.builder()
                    .label(BAND_LABELS[b])
                    .minScore(min)
                    .maxScore(max)
                    .observations(inBand.size())
                    .winRate(bandWin)
                    .avgReturnPct(avgReturn(inBand))
                    .medianReturnPct(medianReturn(inBand))
                    .edgeOverBaseline(bandWin == null || baselineWin == null
                            ? null : bandWin.subtract(baselineWin))
                    .build());
        }

        return BacktestResultDto.builder()
                .horizonDays(horizonDays)
                .stepDays(stepDays)
                .symbolsTested(symbolsTested)
                .totalObservations(observations.size())
                .baselineWinRate(baselineWin)
                .baselineAvgReturnPct(baselineAvg)
                .bands(bands)
                .methodology(List.of(
                        "Only the timing component is replayed. Fundamentals are stored as current "
                                + "values with no history, so scoring a past date with them would use "
                                + "information that did not exist then.",
                        "Each observation scores a date using only closes up to that date, then measures "
                                + "the return to the close nearest " + horizonDays + " days later.",
                        "Evaluation dates step " + stepDays + " trading days apart per symbol so "
                                + "consecutive observations do not measure the same move twice.",
                        "Compare each band against the baseline win rate, not against 50%. A band only "
                                + "shows an edge if it beats what every observation did on average.",
                        "Backed by roughly a year of stored history, so bands with few observations are "
                                + "noise, not evidence."))
                .build();
    }

    private BigDecimal winRate(List<Observation> observations) {
        if (observations.isEmpty()) {
            return null;
        }
        long wins = observations.stream().filter(o -> o.forwardReturnPct().signum() > 0).count();
        return BigDecimal.valueOf(wins * 100.0 / observations.size()).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal avgReturn(List<Observation> observations) {
        if (observations.isEmpty()) {
            return null;
        }
        BigDecimal sum = observations.stream()
                .map(Observation::forwardReturnPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(observations.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal medianReturn(List<Observation> observations) {
        if (observations.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = observations.stream()
                .map(Observation::forwardReturnPct)
                .sorted(Comparator.naturalOrder())
                .toList();
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return sorted.get(mid - 1).add(sorted.get(mid))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
