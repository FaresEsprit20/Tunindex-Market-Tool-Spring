package com.tunindex.market_tool.collector.services.fundamentals;

import com.tunindex.market_tool.collector.entities.Stock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a secondary source is current enough to be believed.
 *
 * <p>A fallback exists to remove blanks. It is only an improvement if what it
 * supplies is true today - a stale figure is strictly worse than the blank it
 * replaces, because a blank is visibly unknown and a stale number is
 * invisibly wrong.
 *
 * <p>This is not hypothetical. Two public sources covering this exchange
 * publish confident, well-formatted, badly out-of-date figures:
 *
 * <ul>
 *   <li>one shows Amen Bank at 59.96 and Air Liquide at 179.01, against live
 *       prices of 90.50 and 218.26 - months behind, under a recent-looking
 *       date column;
 *   <li>another prints today's date in its header while quoting BIAT at
 *       144.90 against a live 161.50, and Amen Bank at 26.50 against 90.50.
 * </ul>
 *
 * <p>Neither announces that it is stale. So rather than trusting a source
 * because it looks maintained, every source that also publishes prices is
 * asked for prices we can already verify. It is believed for the rest of its
 * data only if those agree with the live market.
 *
 * <p>The check is deliberately on price, not on the fundamentals we actually
 * want: price is the one field we hold independently and know to be current,
 * which makes it the only honest yardstick available. A source whose prices
 * are months old is not a source whose earnings are current.
 */
@Service
@Slf4j
public class SourceFreshnessValidator {

    /**
     * How far a source's price may sit from ours before the whole source is
     * rejected, in percent.
     *
     * <p>Five percent is loose enough to absorb a source quoting a previous
     * close against our intraday last, and tight enough to catch the cases
     * above, which are out by 10% to 65%.
     */
    private static final BigDecimal MAX_DEVIATION_PCT = new BigDecimal("5");

    /**
     * How many overlapping symbols must be checkable.
     *
     * <p>Below this the sample says more about which symbols happened to
     * match than about the source, so it is treated as unverifiable - and an
     * unverifiable source is not used.
     */
    private static final int MIN_SAMPLE = 5;

    /**
     * The share of sampled symbols that must agree.
     *
     * <p>A median would hide a source that is right about liquid names and
     * wrong about everything else; requiring most of the sample to agree does
     * not.
     */
    private static final double MIN_AGREEMENT = 0.8;

    /**
     * Checks a source's prices against ours.
     *
     * @param sourceName    for the log line, so a rejection is explainable
     * @param sourcePrices  the candidate source's price per symbol
     * @param stocks        our stocks, holding prices already verified live
     * @return true if the source may be believed for its other fields
     */
    public boolean isCurrent(String sourceName,
                             Map<String, BigDecimal> sourcePrices,
                             List<Stock> stocks) {

        if (sourcePrices == null || sourcePrices.isEmpty()) {
            log.info("Source {} rejected: published no prices to verify against", sourceName);
            return false;
        }

        List<BigDecimal> deviations = new ArrayList<>();
        int agreed = 0;

        for (Stock stock : stocks) {
            BigDecimal ours = stock.getPriceData() == null
                    ? null : stock.getPriceData().getLastPrice();
            BigDecimal theirs = sourcePrices.get(stock.getSymbol());

            if (ours == null || ours.signum() <= 0 || theirs == null || theirs.signum() <= 0) {
                continue;
            }

            BigDecimal deviation = theirs.subtract(ours)
                    .abs()
                    .divide(ours, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            deviations.add(deviation);
            if (deviation.compareTo(MAX_DEVIATION_PCT) <= 0) {
                agreed++;
            }
        }

        if (deviations.size() < MIN_SAMPLE) {
            log.info("Source {} rejected: only {} comparable symbols, cannot verify freshness",
                    sourceName, deviations.size());
            return false;
        }

        double agreement = (double) agreed / deviations.size();
        Collections.sort(deviations);
        BigDecimal median = deviations.get(deviations.size() / 2);

        if (agreement < MIN_AGREEMENT) {
            log.warn("Source {} rejected as stale: {}/{} symbols within {}% "
                            + "(median deviation {}%) - its other fields will not be used",
                    sourceName, agreed, deviations.size(), MAX_DEVIATION_PCT,
                    median.setScale(2, RoundingMode.HALF_UP));
            return false;
        }

        log.info("Source {} accepted as current: {}/{} symbols within {}% (median {}%)",
                sourceName, agreed, deviations.size(), MAX_DEVIATION_PCT,
                median.setScale(2, RoundingMode.HALF_UP));
        return true;
    }
}
