package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.services.fundamentals.StockMerger;
import com.tunindex.market_tool.common.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.common.entities.embedded.FundamentalData;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import com.tunindex.market_tool.common.entities.embedded.RatiosData;
import com.tunindex.market_tool.common.entities.embedded.TechnicalData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rule that a failed scrape must not erase good data.
 *
 * <p>Saving a stock replaces its row, and the supplementary pages a symbol is
 * built from are allowed to fail so that one missing page does not discard the
 * whole company. Those two facts together mean a page timing out writes nulls
 * over figures we already hold - on one run that took price-to-book from
 * complete to nineteen blanks without a single value having actually changed.
 *
 * <p>The distinction being protected: a null on the incoming record means
 * "this scrape did not observe it", never "this is no longer true". A value
 * the scrape *did* collect always wins, including one that moved.
 */
@DisplayName("StockMerger")
class StockMergerTest {

    private final StockMerger merger = new StockMerger();

    private Stock stock() {
        Stock stock = new Stock();
        stock.setSymbol("TEST");
        stock.setPriceData(new PriceData());
        stock.setFundamentalData(new FundamentalData());
        stock.setRatiosData(new RatiosData());
        stock.setTechnicalData(new TechnicalData());
        stock.setCalculatedValues(new CalculatedValues());
        return stock;
    }

    @Test
    @DisplayName("carries forward a figure the new scrape failed to collect")
    void carriesForwardMissingValue() {
        Stock existing = stock();
        existing.getRatiosData().setPriceToBook(new BigDecimal("1.51"));

        // The statistics page timed out, so this scrape has no P/B.
        Stock incoming = stock();
        incoming.getPriceData().setLastPrice(new BigDecimal("8.30"));

        assertThat(merger.carryForward(incoming, existing)).contains("ratios.PriceToBook");
        assertThat(incoming.getRatiosData().getPriceToBook()).isEqualByComparingTo("1.51");
    }

    @Test
    @DisplayName("never overwrites a value the scrape did collect")
    void freshValueWins() {
        Stock existing = stock();
        existing.getPriceData().setLastPrice(new BigDecimal("8.30"));

        Stock incoming = stock();
        incoming.getPriceData().setLastPrice(new BigDecimal("8.20"));

        merger.carryForward(incoming, existing);

        // The price genuinely moved; carrying the old one forward would pin
        // the app to a stale quote forever.
        assertThat(incoming.getPriceData().getLastPrice()).isEqualByComparingTo("8.20");
    }

    @Test
    @DisplayName("preserves the computed beta a scrape has no opinion on")
    void preservesComputedBeta() {
        // Beta is computed against the TUNINDEX after the pipeline, so the
        // scrape that follows carries either nothing or a foreign-index value.
        Stock existing = stock();
        existing.getTechnicalData().setBeta(new BigDecimal("1.35"));

        Stock incoming = stock();

        merger.carryForward(incoming, existing);

        assertThat(incoming.getTechnicalData().getBeta()).isEqualByComparingTo("1.35");
    }

    @Test
    @DisplayName("carries forward across every embedded block")
    void coversAllBlocks() {
        Stock existing = stock();
        existing.getFundamentalData().setEps(new BigDecimal("0.79"));
        existing.getFundamentalData().setOneYearReturn(new BigDecimal("59.92"));
        existing.getCalculatedValues().setBookValuePerShare(new BigDecimal("5.60"));
        existing.getPriceData().setWeek52High(new BigDecimal("10.15"));

        Stock incoming = stock();

        merger.carryForward(incoming, existing);

        assertThat(incoming.getFundamentalData().getEps()).isEqualByComparingTo("0.79");
        assertThat(incoming.getFundamentalData().getOneYearReturn()).isEqualByComparingTo("59.92");
        assertThat(incoming.getCalculatedValues().getBookValuePerShare()).isEqualByComparingTo("5.60");
        assertThat(incoming.getPriceData().getWeek52High()).isEqualByComparingTo("10.15");
    }

    @Test
    @DisplayName("does nothing when there is no previous record")
    void toleratesFirstInsert() {
        Stock incoming = stock();

        assertThat(merger.carryForward(incoming, null)).isEmpty();
        assertThat(merger.carryForward(null, stock())).isEmpty();
    }

    @Test
    @DisplayName("tolerates an embedded block the scrape did not build at all")
    void toleratesNullBlocks() {
        Stock existing = stock();
        existing.getFundamentalData().setEps(new BigDecimal("0.79"));

        Stock incoming = new Stock();
        incoming.setSymbol("TEST");

        // No exception, and nothing invented: a block the new record lacks
        // entirely is the caller's business, not the merger's.
        assertThat(merger.carryForward(incoming, existing)).isEmpty();
    }
}
