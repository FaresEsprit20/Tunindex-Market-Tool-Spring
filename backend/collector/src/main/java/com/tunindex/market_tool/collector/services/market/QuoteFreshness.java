package com.tunindex.market_tool.collector.services.market;

import com.tunindex.market_tool.collector.entities.Stock;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Whether a stored quote is recent enough to present as today's price.
 *
 * <p>One definition, used by everything that shows a figure as current:
 * breadth, movers, unusual activity and the opportunity scorer. It lived as a
 * private constant in three of those and was missing from the fourth, which
 * is how a delisted symbol ended up recommended as a STRONG BUY.
 *
 * <p>The distinction that matters is between {@code Stock.lastUpdate} and
 * {@code PriceData.liveQuoteAt}. A pipeline run writes the row — and stamps
 * {@code lastUpdate} with the current time — even when the exchange fetch
 * failed, in which case the row keeps the fundamentals provider's price,
 * which lags a full trading day. Only {@code liveQuoteAt} records that we
 * actually read the exchange.
 */
public final class QuoteFreshness {

    /**
     * How old a live quote may be before the name is treated as unpriced.
     *
     * <p>Generous — a full trading day plus a margin — because the purpose is
     * not freshness for its own sake. It is to exclude names whose exchange
     * page we can no longer read at all, which stay frozen forever.
     */
    public static final Duration MAX_QUOTE_AGE = Duration.ofHours(30);

    private QuoteFreshness() {
    }

    public static boolean isFresh(Stock stock) {
        if (stock == null || stock.getPriceData() == null) {
            return false;
        }
        LocalDateTime quoteAt = stock.getPriceData().getLiveQuoteAt();
        return quoteAt != null && quoteAt.isAfter(LocalDateTime.now().minus(MAX_QUOTE_AGE));
    }

    /** Hours since the last successful live quote, or null if there never was one. */
    public static Long hoursSinceQuote(Stock stock) {
        if (stock == null || stock.getPriceData() == null
                || stock.getPriceData().getLiveQuoteAt() == null) {
            return null;
        }
        return Duration.between(stock.getPriceData().getLiveQuoteAt(), LocalDateTime.now()).toHours();
    }
}
