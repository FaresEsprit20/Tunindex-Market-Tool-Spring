package com.tunindex.market_tool.collector.services.scoring;

import com.tunindex.market_tool.collector.dto.scoring.OpportunityScoreDto;
import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.ScoreSnapshot;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.ScoreSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Records every stock's Tunindex Score once a day.
 *
 * <p>Without this the scorer computes on request and discards, which makes
 * two things impossible: showing whether a score is improving, and checking
 * after the fact whether high scores were followed by gains. One row per
 * symbol per day is all that is needed for both, and the unique constraint
 * on (symbol, snapshotDate) makes a re-run idempotent rather than
 * duplicating the day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreSnapshotService {

    private final OpportunityService opportunityService;
    private final ScoreSnapshotRepository snapshotRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    /**
     * Runs daily after the close. Scoring the whole exchange is heavy, so
     * this deliberately does not run on every pipeline cycle — a score
     * series only needs one point per day to be useful.
     */
    @Scheduled(cron = "${market-tool.snapshots.cron:0 30 15 * * MON-FRI}", zone = "Africa/Tunis")
    public void captureDaily() {
        capture(LocalDate.now());
    }

    /**
     * @return how many rows were written or refreshed.
     */
    @Transactional
    public int capture(LocalDate date) {
        // includeNews=false: the news component reads cached headlines, and
        // pulling them for all 69 symbols would triple the run for a
        // component worth 5% of the blend.
        List<OpportunityScoreDto> scores = opportunityService.findOpportunities(200, Integer.MIN_VALUE, false);

        int written = 0;
        for (OpportunityScoreDto score : scores) {
            ScoreSnapshot snapshot = snapshotRepository
                    .findBySymbolAndSnapshotDate(score.getSymbol(), date)
                    .orElseGet(() -> ScoreSnapshot.builder()
                            .symbol(score.getSymbol())
                            .snapshotDate(date)
                            .build());

            snapshot.setOverallScore(score.getOverallScore());
            snapshot.setVerdict(score.getVerdict());
            snapshot.setValuationScore(score.getValuationScore());
            snapshot.setTimingScore(score.getTimingScore());
            snapshot.setFinancialHealthScore(score.getFinancialHealthScore());
            snapshot.setIncomeScore(score.getIncomeScore());
            snapshot.setMomentumScore(score.getMomentumScore());
            snapshot.setNewsScore(score.getNewsScore());
            snapshot.setDataCompleteness(score.getDataCompleteness());
            snapshot.setClosePrice(closeOn(score.getSymbol(), date));

            snapshotRepository.save(snapshot);
            written++;
        }

        log.info("📸 Score snapshot for {}: {} symbols recorded", date, written);
        return written;
    }

    /** Latest stored close on or before the snapshot date, as the entry price. */
    private BigDecimal closeOn(String symbol, LocalDate date) {
        List<PriceHistory> rows = priceHistoryRepository
                .findBySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(symbol, date, Limit.of(1));
        return rows.isEmpty() ? null : rows.get(0).getClose();
    }

    @Transactional(readOnly = true)
    public List<ScoreSnapshot> history(String symbol, int days) {
        return snapshotRepository.findBySymbolAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(
                symbol.trim().toUpperCase(), LocalDate.now().minusDays(days));
    }
}
