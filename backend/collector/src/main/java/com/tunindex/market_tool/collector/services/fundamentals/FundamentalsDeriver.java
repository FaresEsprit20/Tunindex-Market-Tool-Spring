package com.tunindex.market_tool.collector.services.fundamentals;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.common.entities.embedded.CalculatedValues;
import com.tunindex.market_tool.common.entities.embedded.FundamentalData;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import com.tunindex.market_tool.common.entities.embedded.RatiosData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills gaps in a stock's fundamentals from figures we already hold.
 *
 * <p>These ratios are not independent measurements - they are arithmetic
 * identities over price, earnings and book value. When a source omits one side
 * of an identity but publishes the other, deriving the missing side is exact,
 * and unlike a second scraped source it cannot disagree with itself.
 *
 * <p>The identities:
 *
 * <ul>
 *   <li>{@code P/E = price / EPS}, and its inverse
 *   <li>{@code P/B = price / BVPS}, and its inverse
 *   <li>{@code payout ratio = dividend yield x P/E}
 * </ul>
 *
 * <p>This is the single place these are computed. The provider enrichers used
 * to derive EPS and BVPS themselves, which meant two code paths could produce
 * two different values for the same field depending on which provider last
 * touched the stock.
 *
 * <p><b>What this deliberately does not do</b> is invent a value from a peer
 * company, a sector average, or a stale figure. A gap that no identity closes
 * stays a gap, because the scorer treats null as "unknown" and drops that
 * component - whereas a plausible-looking wrong number silently changes a
 * recommendation.
 *
 * <p>Order matters: P/E is derived before the payout ratio, which consumes it.
 */
@Service
@Slf4j
public class FundamentalsDeriver {

    private static final int RATIO_SCALE = 4;
    private static final int PERCENT_SCALE = 2;

    /**
     * Beyond this, a ratio is a rounding artefact on a near-zero denominator
     * rather than a valuation. Left null instead of stored.
     */
    private static final BigDecimal MAX_PLAUSIBLE_RATIO = new BigDecimal("10000");

    /**
     * A payout above 100% is real - companies do dip into reserves - but far
     * beyond it means one of the two inputs is wrong.
     */
    private static final BigDecimal MAX_PLAUSIBLE_PAYOUT = new BigDecimal("300");

    /** Derives what it can, in place. Returns the names of the fields it filled. */
    public List<String> derive(Stock stock) {
        List<String> derived = new ArrayList<>();
        if (stock == null) {
            return derived;
        }

        // Providers populate whichever blocks they have data for, so a stock
        // can arrive with some embedded objects still null.
        if (stock.getPriceData() == null) {
            stock.setPriceData(new PriceData());
        }
        if (stock.getFundamentalData() == null) {
            stock.setFundamentalData(new FundamentalData());
        }
        if (stock.getRatiosData() == null) {
            stock.setRatiosData(new RatiosData());
        }
        if (stock.getCalculatedValues() == null) {
            stock.setCalculatedValues(new CalculatedValues());
        }

        BigDecimal price = stock.getPriceData().getLastPrice();
        if (price == null || price.signum() <= 0) {
            // Every identity below runs through price.
            return derived;
        }

        derivePeRatio(stock, price, derived);
        deriveEps(stock, price, derived);
        derivePriceToBook(stock, price, derived);
        deriveBookValuePerShare(stock, price, derived);
        derivePayoutRatio(stock, derived);

        if (!derived.isEmpty()) {
            log.debug("Derived {} for {}", derived, stock.getSymbol());
        }
        return derived;
    }

    /** P/E = price / EPS. */
    private void derivePeRatio(Stock stock, BigDecimal price, List<String> derived) {
        FundamentalData fundamentals = stock.getFundamentalData();
        if (fundamentals.getPeRatio() != null) {
            return;
        }
        BigDecimal eps = fundamentals.getEps();
        // A loss-making company has no meaningful P/E. Null is the honest
        // result; a negative one would sort as "cheap" in a value screen.
        if (eps == null || eps.signum() <= 0) {
            return;
        }
        BigDecimal pe = divide(price, eps);
        if (plausible(pe)) {
            fundamentals.setPeRatio(pe);
            derived.add("peRatio");
        }
    }

    /** EPS = price / P/E - the same identity read the other way. */
    private void deriveEps(Stock stock, BigDecimal price, List<String> derived) {
        FundamentalData fundamentals = stock.getFundamentalData();
        if (fundamentals.getEps() != null) {
            return;
        }
        BigDecimal pe = fundamentals.getPeRatio();
        if (pe == null || pe.signum() <= 0) {
            return;
        }
        fundamentals.setEps(divide(price, pe));
        derived.add("eps");
    }

    /** P/B = price / book value per share. */
    private void derivePriceToBook(Stock stock, BigDecimal price, List<String> derived) {
        if (stock.getRatiosData().getPriceToBook() != null) {
            return;
        }
        BigDecimal bvps = stock.getCalculatedValues().getBookValuePerShare();
        if (bvps == null || bvps.signum() <= 0) {
            return;
        }
        BigDecimal pb = divide(price, bvps);
        if (plausible(pb)) {
            stock.getRatiosData().setPriceToBook(pb);
            derived.add("priceToBook");
        }
    }

    /**
     * Book value per share = price / P/B.
     *
     * <p>The most valuable derivation here. The source reports book value as
     * "n/a" for much of this market while still publishing the P/B ratio, and
     * book value feeds the Graham fair value and margin of safety the scorer
     * depends on. Without this, those stay null for a large share of the
     * exchange even though the number is recoverable exactly.
     */
    private void deriveBookValuePerShare(Stock stock, BigDecimal price, List<String> derived) {
        CalculatedValues calculated = stock.getCalculatedValues();
        if (calculated.getBookValuePerShare() != null) {
            return;
        }
        BigDecimal pb = stock.getRatiosData().getPriceToBook();
        if (pb == null || pb.signum() <= 0) {
            return;
        }
        calculated.setBookValuePerShare(divide(price, pb));
        derived.add("bookValuePerShare");
    }

    /**
     * Payout ratio = dividend yield x P/E.
     *
     * <p>Both sides are expressed against price, so price cancels and what
     * remains is dividend over earnings - the payout ratio by definition.
     */
    private void derivePayoutRatio(Stock stock, List<String> derived) {
        FundamentalData fundamentals = stock.getFundamentalData();
        if (fundamentals.getPayoutRatio() != null) {
            return;
        }
        BigDecimal dividendYield = fundamentals.getDividendYield();
        BigDecimal pe = fundamentals.getPeRatio();
        if (dividendYield == null || pe == null || dividendYield.signum() < 0 || pe.signum() <= 0) {
            return;
        }
        BigDecimal payout = dividendYield.multiply(pe).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        if (payout.compareTo(MAX_PLAUSIBLE_PAYOUT) > 0) {
            return;
        }
        fundamentals.setPayoutRatio(payout);
        derived.add("payoutRatio");
    }

    private BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private boolean plausible(BigDecimal ratio) {
        return ratio.abs().compareTo(MAX_PLAUSIBLE_RATIO) <= 0;
    }
}
