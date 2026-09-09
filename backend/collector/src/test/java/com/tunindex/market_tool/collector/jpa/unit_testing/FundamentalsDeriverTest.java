package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.services.fundamentals.FundamentalsDeriver;
import com.tunindex.market_tool.common.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.common.entities.embedded.FundamentalData;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import com.tunindex.market_tool.common.entities.embedded.RatiosData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the arithmetic identities the scorer depends on.
 *
 * <p>Two properties matter more than the individual sums. First, a derived
 * value must equal what the source would have published, so a stock filled in
 * here is interchangeable with one scraped complete. Second, the deriver must
 * decline rather than guess: a fabricated ratio would flow into the
 * opportunity score and change a recommendation with nothing to flag it.
 */
@DisplayName("FundamentalsDeriver")
class FundamentalsDeriverTest {

    private final FundamentalsDeriver deriver = new FundamentalsDeriver();

    private Stock stock(String price) {
        Stock stock = new Stock();
        stock.setSymbol("TEST");
        PriceData priceData = new PriceData();
        if (price != null) {
            priceData.setLastPrice(new BigDecimal(price));
        }
        stock.setPriceData(priceData);
        stock.setFundamentalData(new FundamentalData());
        stock.setRatiosData(new RatiosData());
        stock.setCalculatedValues(new CalculatedValues());
        return stock;
    }

    @Test
    @DisplayName("recovers book value per share from the published P/B ratio")
    void derivesBookValueFromPriceToBook() {
        // The real shape of the gap: stockanalysis prints "n/a" for book value
        // on most of this market while still publishing P/B.
        Stock stock = stock("7.55");
        stock.getRatiosData().setPriceToBook(new BigDecimal("1.51"));

        assertThat(deriver.derive(stock)).contains("bookValuePerShare");
        assertThat(stock.getCalculatedValues().getBookValuePerShare())
                .isEqualByComparingTo("5.0000");
    }

    @Test
    @DisplayName("recovers the P/B ratio from a published book value")
    void derivesPriceToBookFromBookValue() {
        Stock stock = stock("7.55");
        stock.getCalculatedValues().setBookValuePerShare(new BigDecimal("5.00"));

        assertThat(deriver.derive(stock)).contains("priceToBook");
        assertThat(stock.getRatiosData().getPriceToBook()).isEqualByComparingTo("1.5100");
    }

    @Test
    @DisplayName("derives P/E from EPS and leaves a supplied P/E untouched")
    void derivesPeRatio() {
        Stock stock = stock("10.00");
        stock.getFundamentalData().setEps(new BigDecimal("1.25"));

        assertThat(deriver.derive(stock)).contains("peRatio");
        assertThat(stock.getFundamentalData().getPeRatio()).isEqualByComparingTo("8.0000");

        // A second pass must not rewrite what is already there.
        Stock scraped = stock("10.00");
        scraped.getFundamentalData().setEps(new BigDecimal("1.25"));
        scraped.getFundamentalData().setPeRatio(new BigDecimal("9.9"));

        assertThat(deriver.derive(scraped)).doesNotContain("peRatio");
        assertThat(scraped.getFundamentalData().getPeRatio()).isEqualByComparingTo("9.9");
    }

    @Test
    @DisplayName("derives the payout ratio as dividend yield x P/E")
    void derivesPayoutRatio() {
        Stock stock = stock("7.55");
        // 7.21% yield on a P/E of 8 is a payout of ~57.7% of earnings.
        stock.getFundamentalData().setDividendYield(new BigDecimal("7.21"));
        stock.getFundamentalData().setEps(new BigDecimal("0.94375"));

        assertThat(deriver.derive(stock)).contains("payoutRatio");
        assertThat(stock.getFundamentalData().getPayoutRatio())
                .isEqualByComparingTo("57.68");
    }

    @Test
    @DisplayName("derives the true negative P/E for a loss-making company")
    void derivesNegativeEarnings() {
        Stock stock = stock("10.00");
        stock.getFundamentalData().setEps(new BigDecimal("-2.00"));

        assertThat(deriver.derive(stock)).contains("peRatio");
        // -5 is the real ratio, and the scorer already treats any P/E at or
        // below zero as the worst case - so this cannot read as cheapness,
        // and it beats a blank cell that explains nothing.
        assertThat(stock.getFundamentalData().getPeRatio()).isEqualByComparingTo("-5.0000");
    }

    @Test
    @DisplayName("declines a P/E only when earnings are exactly zero")
    void refusesZeroEarnings() {
        Stock stock = stock("10.00");
        stock.getFundamentalData().setEps(BigDecimal.ZERO);

        // Division by zero: undefined, not merely negative.
        assertThat(deriver.derive(stock)).doesNotContain("peRatio");
        assertThat(stock.getFundamentalData().getPeRatio()).isNull();
    }

    @Test
    @DisplayName("declines every derivation when the price is missing")
    void refusesWithoutPrice() {
        Stock stock = stock(null);
        stock.getFundamentalData().setEps(new BigDecimal("1.25"));
        stock.getRatiosData().setPriceToBook(new BigDecimal("1.51"));

        assertThat(deriver.derive(stock)).isEmpty();
        assertThat(stock.getCalculatedValues().getBookValuePerShare()).isNull();
    }

    @Test
    @DisplayName("rejects a payout ratio that implies a broken input")
    void rejectsImplausiblePayout() {
        Stock stock = stock("10.00");
        stock.getFundamentalData().setPeRatio(new BigDecimal("200"));
        stock.getFundamentalData().setDividendYield(new BigDecimal("50"));

        // 200 x 50 = 10000% of earnings — one of the two figures is wrong, and
        // storing the product would corrupt the dividend-quality score.
        assertThat(deriver.derive(stock)).doesNotContain("payoutRatio");
        assertThat(stock.getFundamentalData().getPayoutRatio()).isNull();
    }

    @Test
    @DisplayName("creates missing embedded blocks rather than throwing")
    void toleratesNullEmbeddedBlocks() {
        Stock stock = new Stock();
        stock.setSymbol("BARE");

        assertThat(deriver.derive(stock)).isEmpty();
        assertThat(stock.getFundamentalData()).isNotNull();
        assertThat(stock.getCalculatedValues()).isNotNull();
    }

    @Test
    @DisplayName("round-trips: derived book value reproduces the source P/B")
    void derivationsAreConsistent() {
        Stock stock = stock("7.55");
        stock.getRatiosData().setPriceToBook(new BigDecimal("1.51"));
        deriver.derive(stock);

        BigDecimal bvps = stock.getCalculatedValues().getBookValuePerShare();

        Stock mirror = stock("7.55");
        mirror.getCalculatedValues().setBookValuePerShare(bvps);
        deriver.derive(mirror);

        // Whichever side the source published, the pair agrees.
        assertThat(mirror.getRatiosData().getPriceToBook()).isEqualByComparingTo("1.5100");
    }
}
