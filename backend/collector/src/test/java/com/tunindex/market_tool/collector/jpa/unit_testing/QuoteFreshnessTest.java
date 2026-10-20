package com.tunindex.market_tool.collector.jpa.unit_testing;

import com.tunindex.market_tool.collector.entities.Stock;
import com.tunindex.market_tool.collector.services.market.QuoteFreshness;
import com.tunindex.market_tool.common.entities.embedded.PriceData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The single rule separating "today's price" from "the last price we ever
 * managed to read".
 *
 * <p>Worth its own test because getting it wrong is not a cosmetic bug: a
 * delisted symbol with a frozen price looks permanently cheap and permanently
 * at its 52-week low, and it reached the top of both the gainers list and the
 * STRONG BUY recommendations on exactly that basis.
 */
@DisplayName("QuoteFreshness")
class QuoteFreshnessTest {

    private Stock stock(LocalDateTime liveQuoteAt, LocalDateTime lastUpdate) {
        return Stock.builder()
                .symbol("TEST")
                .lastUpdate(lastUpdate)
                .priceData(PriceData.builder()
                        .lastPrice(new BigDecimal("10"))
                        .prevClose(new BigDecimal("9"))
                        .liveQuoteAt(liveQuoteAt)
                        .build())
                .build();
    }

    @Test
    @DisplayName("a quote read moments ago is fresh")
    void recentQuoteIsFresh() {
        assertThat(QuoteFreshness.isFresh(stock(LocalDateTime.now().minusMinutes(5), LocalDateTime.now())))
                .isTrue();
    }

    @Test
    @DisplayName("yesterday's quote is still fresh — a closed market is not a stale one")
    void overnightQuoteIsFresh() {
        assertThat(QuoteFreshness.isFresh(stock(LocalDateTime.now().minusHours(20), LocalDateTime.now())))
                .isTrue();
    }

    @Test
    @DisplayName("a quote older than the ceiling is stale")
    void oldQuoteIsStale() {
        assertThat(QuoteFreshness.isFresh(stock(LocalDateTime.now().minusHours(40), LocalDateTime.now())))
                .isFalse();
    }

    @Test
    @DisplayName("a fresh row write does not make a stale quote fresh")
    void lastUpdateDoesNotCountAsAQuote() {
        // The exact production trap: the pipeline rewrites the row every run
        // and stamps lastUpdate with now, even when the exchange fetch failed
        // and the price is the fundamentals provider's day-old one.
        Stock delisted = stock(LocalDateTime.now().minusDays(9), LocalDateTime.now());

        assertThat(delisted.getLastUpdate()).isAfter(LocalDateTime.now().minusMinutes(1));
        assertThat(QuoteFreshness.isFresh(delisted)).isFalse();
    }

    @Test
    @DisplayName("a symbol never quoted is never fresh")
    void neverQuotedIsStale() {
        assertThat(QuoteFreshness.isFresh(stock(null, LocalDateTime.now()))).isFalse();
        assertThat(QuoteFreshness.hoursSinceQuote(stock(null, LocalDateTime.now()))).isNull();
    }

    @Test
    @DisplayName("a stock with no price data at all is not fresh, and does not throw")
    void missingPriceData() {
        assertThat(QuoteFreshness.isFresh(Stock.builder().symbol("TEST").build())).isFalse();
        assertThat(QuoteFreshness.isFresh(null)).isFalse();
    }

    @Test
    @DisplayName("reports how long ago the quote was, for the warning text")
    void reportsAge() {
        assertThat(QuoteFreshness.hoursSinceQuote(stock(LocalDateTime.now().minusHours(50), LocalDateTime.now())))
                .isEqualTo(50L);
    }
}
