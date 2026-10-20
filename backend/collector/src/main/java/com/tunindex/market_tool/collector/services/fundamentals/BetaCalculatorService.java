package com.tunindex.market_tool.collector.services.fundamentals;

import com.tunindex.market_tool.collector.entities.PriceHistory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Computes beta against the TUNINDEX from price history we already hold.
 *
 * <p>Beta is the one "fundamental" on the list that is not a reported figure
 * at all - it is a statistic over returns. That makes it the wrong thing to
 * scrape and the right thing to calculate: any source publishing a beta for a
 * Tunisian listing computed it from the same public prices, and we cannot see
 * which index or window they used. Computing it here fixes both the benchmark
 * (TUNINDEX, which is what a Tunisian portfolio is actually measured against)
 * and the window, and it cannot go stale while the price history is current.
 *
 * <p>It also removes a single-source dependency: beta reached us from exactly
 * one provider, so losing that provider lost the field for every stock.
 *
 * <p><b>Alignment is the part that matters.</b> Returns must be paired by
 * trade date, never by position in the series. Thinly traded shares here miss
 * many sessions - some quote only at a weekly fixing - so the nth bar of a
 * stock and the nth bar of the index are usually different days. Pairing by
 * position would silently correlate a stock's Monday with the index's
 * Thursday and produce a confident, meaningless number.
 */
@Service
@Slf4j
public class BetaCalculatorService {

    /**
     * Minimum paired daily returns before a beta is meaningful.
     *
     * <p>Sixty is about three months of sessions. Below that the estimate
     * swings wildly with single days, and a beta the scorer treats as a risk
     * measure would be mostly noise.
     */
    private static final int MIN_PAIRED_RETURNS = 60;

    /**
     * Beyond this, the regression has found an artefact rather than a risk
     * profile - typically a near-zero index variance over a flat window.
     */
    private static final BigDecimal MAX_PLAUSIBLE_BETA = new BigDecimal("5");

    /**
     * Daily closes by trade date, for pairing against a stock's own dates.
     */
    public static Map<LocalDate, BigDecimal> closesByDate(List<PriceHistory> history) {
        Map<LocalDate, BigDecimal> closes = new java.util.HashMap<>();
        for (PriceHistory bar : history) {
            if (bar.getTradeDate() != null && bar.getClose() != null && bar.getClose().signum() > 0) {
                closes.put(bar.getTradeDate(), bar.getClose());
            }
        }
        return closes;
    }

    /**
     * Beta of one stock against the index, or null when it cannot be measured.
     *
     * @param stockHistory  the stock's bars, ascending by date
     * @param indexCloses   index closes keyed by trade date
     */
    public BigDecimal beta(List<PriceHistory> stockHistory, Map<LocalDate, BigDecimal> indexCloses) {
        if (stockHistory == null || indexCloses == null || indexCloses.isEmpty()) {
            return null;
        }

        List<Double> stockReturns = new ArrayList<>();
        List<Double> indexReturns = new ArrayList<>();

        PriceHistory previous = null;
        for (PriceHistory bar : stockHistory) {
            if (bar.getTradeDate() == null || bar.getClose() == null || bar.getClose().signum() <= 0) {
                continue;
            }
            if (previous != null) {
                BigDecimal indexNow = indexCloses.get(bar.getTradeDate());
                BigDecimal indexBefore = indexCloses.get(previous.getTradeDate());

                // Both ends of the stock's step must exist on the index too,
                // otherwise the two returns cover different spans of time.
                if (indexNow != null && indexBefore != null && indexBefore.signum() > 0) {
                    double stockReturn = ratio(bar.getClose(), previous.getClose());
                    double indexReturn = ratio(indexNow, indexBefore);
                    stockReturns.add(stockReturn);
                    indexReturns.add(indexReturn);
                }
            }
            previous = bar;
        }

        if (stockReturns.size() < MIN_PAIRED_RETURNS) {
            return null;
        }

        double indexMean = mean(indexReturns);
        double stockMean = mean(stockReturns);

        double covariance = 0;
        double indexVariance = 0;
        for (int i = 0; i < stockReturns.size(); i++) {
            double indexDeviation = indexReturns.get(i) - indexMean;
            covariance += (stockReturns.get(i) - stockMean) * indexDeviation;
            indexVariance += indexDeviation * indexDeviation;
        }

        if (indexVariance == 0) {
            // A perfectly flat index over the window: beta is undefined, not
            // zero, and zero would read as "no market risk".
            return null;
        }

        BigDecimal beta = BigDecimal.valueOf(covariance / indexVariance)
                .setScale(2, RoundingMode.HALF_UP);

        if (beta.abs().compareTo(MAX_PLAUSIBLE_BETA) > 0) {
            log.debug("Discarding implausible beta {}", beta);
            return null;
        }
        return beta;
    }

    private double ratio(BigDecimal now, BigDecimal before) {
        return now.subtract(before)
                .divide(before, 8, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double mean(List<Double> values) {
        double total = 0;
        for (double v : values) {
            total += v;
        }
        return total / values.size();
    }
}
