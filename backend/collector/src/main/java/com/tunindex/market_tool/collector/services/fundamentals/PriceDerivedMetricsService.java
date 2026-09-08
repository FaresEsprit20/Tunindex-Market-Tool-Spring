package com.tunindex.market_tool.collector.services.fundamentals;

import com.tunindex.market_tool.collector.entities.PriceHistory;
import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.repository.jpa.PriceHistoryRepository;
import com.tunindex.market_tool.collector.repository.jpa.StockRepository;
import com.tunindex.market_tool.common.entities.embedded.FundamentalData;
import com.tunindex.market_tool.common.entities.embedded.VolumeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the two metrics no provider publishes for this market, from the
 * price history we already store.
 *
 * <p>Average volume and one-year return were empty for every stock on the
 * exchange, not because a parser missed them but because the sources simply do
 * not carry them for Tunisian listings. We do hold the underlying daily bars,
 * so these are ours to compute rather than to find.
 *
 * <p>The guards are the substance here. Both metrics name a period - "3 month
 * average", "1 year return" - and computing them over whatever history happens
 * to exist would put a three-week number under a one-year label. That is worse
 * than leaving the field empty, because nothing downstream can tell the
 * difference. So each metric states the coverage it requires and declines when
 * the history is too short or too sparse.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceDerivedMetricsService {

    /** Roughly a calendar quarter, in days. */
    private static final int VOLUME_WINDOW_DAYS = 91;

    /**
     * A quarter holds about 63 trading days. Requiring 30 tolerates holidays
     * and the thin trading typical of small caps here, while still rejecting a
     * window with only a handful of prints in it.
     */
    private static final int MIN_VOLUME_OBSERVATIONS = 30;

    /**
     * How far the bar we measure from may sit from exactly one year back.
     *
     * <p>The bar has to be a real trading day, so it will rarely land on the
     * anniversary itself - a fortnight absorbs holidays and a thin stock's
     * gaps. It must stay small: the lookback holds more than a year of bars,
     * and measuring from the oldest one would put a 400-day change in a field
     * every reader takes to mean twelve months.
     */
    private static final int RETURN_ANCHOR_TOLERANCE_DAYS = 15;

    /**
     * Slightly more than a year, so a bar on either side of the anniversary is
     * available to anchor to.
     */
    private static final int LOOKBACK_DAYS = 400;

    private final StockRepository stockRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    /**
     * Recomputes both metrics for every stock and persists the changes.
     *
     * @return how many stocks had at least one field filled
     */
    @Transactional
    public int refreshAll() {
        List<Stock> stocks = stockRepository.findAll();
        LocalDate from = LocalDate.now().minusDays(LOOKBACK_DAYS);

        int updated = 0;
        int volumeFilled = 0;
        int returnFilled = 0;

        for (Stock stock : stocks) {
            List<PriceHistory> history =
                    priceHistoryRepository.findBySymbolAndTradeDateGreaterThanEqualOrderByTradeDateAsc(
                            stock.getSymbol(), from);
            if (history.isEmpty()) {
                continue;
            }

            boolean changed = false;

            Long avgVolume = averageVolume(history);
            if (avgVolume != null) {
                if (stock.getVolumeData() == null) {
                    stock.setVolumeData(new VolumeData());
                }
                stock.getVolumeData().setAvgVolume3m(avgVolume);
                volumeFilled++;
                changed = true;
            }

            BigDecimal oneYearReturn = oneYearReturn(history);
            if (oneYearReturn != null) {
                if (stock.getFundamentalData() == null) {
                    stock.setFundamentalData(new FundamentalData());
                }
                stock.getFundamentalData().setOneYearReturn(oneYearReturn);
                returnFilled++;
                changed = true;
            }

            if (changed) {
                stockRepository.save(stock);
                updated++;
            }
        }

        log.info("Price-derived metrics: {} stocks updated ({} average volume, {} one-year return)",
                updated, volumeFilled, returnFilled);
        return updated;
    }

    /**
     * Mean daily volume over the trailing quarter.
     *
     * <p>Days with no volume recorded are skipped rather than counted as zero:
     * a missing bar means we have no observation, while a genuine zero-volume
     * day means the stock did not trade. Averaging the former in would
     * understate liquidity for any stock whose history has gaps.
     */
    private Long averageVolume(List<PriceHistory> history) {
        LocalDate cutoff = LocalDate.now().minusDays(VOLUME_WINDOW_DAYS);

        long total = 0;
        int observations = 0;
        for (PriceHistory bar : history) {
            if (bar.getTradeDate() == null || bar.getTradeDate().isBefore(cutoff)) {
                continue;
            }
            if (bar.getVolume() == null) {
                continue;
            }
            total += bar.getVolume();
            observations++;
        }

        if (observations < MIN_VOLUME_OBSERVATIONS) {
            return null;
        }
        return total / observations;
    }

    /**
     * Percentage change over the twelve months ending at the most recent bar.
     *
     * <p>Anchored to the bar nearest one year before the latest close, not to
     * the oldest bar on file. Those are not the same measurement: the lookback
     * deliberately reaches back further than a year so an anchor exists on
     * either side of the anniversary, and taking its far end instead would
     * report a thirteen-month change under a twelve-month name.
     *
     * <p>Declines when no bar lands near the anniversary, which is the case
     * for a recent listing - a stock with four months of history has no
     * one-year return, and saying so is better than returning its four-month
     * one.
     */
    private BigDecimal oneYearReturn(List<PriceHistory> history) {
        List<PriceHistory> usable = new ArrayList<>();
        for (PriceHistory bar : history) {
            if (bar.getClose() != null && bar.getClose().signum() > 0 && bar.getTradeDate() != null) {
                usable.add(bar);
            }
        }
        if (usable.size() < 2) {
            return null;
        }

        PriceHistory newest = usable.get(usable.size() - 1);
        LocalDate anniversary = newest.getTradeDate().minusYears(1);

        PriceHistory anchor = null;
        long bestDistance = Long.MAX_VALUE;
        for (PriceHistory bar : usable) {
            if (bar == newest) {
                continue;
            }
            long distance = Math.abs(ChronoUnit.DAYS.between(bar.getTradeDate(), anniversary));
            if (distance < bestDistance) {
                bestDistance = distance;
                anchor = bar;
            }
        }

        if (anchor == null || bestDistance > RETURN_ANCHOR_TOLERANCE_DAYS) {
            return null;
        }

        return newest.getClose()
                .subtract(anchor.getClose())
                .divide(anchor.getClose(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
