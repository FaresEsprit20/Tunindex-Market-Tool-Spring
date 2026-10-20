package com.tunindex.market_tool.collector.dto.macro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One traded instrument outside the BVMT — a metal, a crypto pair, or a
 * currency cross — with its move since the previous close.
 *
 * <p>Every quote in a banner has to mean the same thing, so the change here is
 * always measured the same way: last price against the previous session's
 * close, from a single provider. Mixing that with, say, a rolling 24-hour
 * window from a different API would put two different definitions of "daily
 * change" side by side under one heading.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketQuoteDto {

    /** Stable key for the UI: GOLD | SILVER | BTC | ETH | SOL | USD_TND | EUR_TND */
    private String key;

    private String label;

    /** The provider's own ticker, so a figure can be traced back. */
    private String symbol;

    private BigDecimal price;

    /** Previous session's close, the baseline the change is measured against. */
    private BigDecimal previousClose;

    private BigDecimal changePct;

    /** Absolute move in the quote currency. */
    private BigDecimal changeValue;

    private String currency;

    /** METAL | CRYPTO | FX — lets the UI group without parsing the key. */
    private String category;

    /**
     * What the change is measured against: {@code PREVIOUS_CLOSE} for
     * exchange-traded instruments, {@code ROLLING_24H} for crypto.
     *
     * <p>Shipped explicitly rather than assumed. Metals and currencies have a
     * daily close to compare with; crypto trades continuously, so a "previous
     * close" there is an arbitrary line and every venue quotes a rolling
     * window instead. Both are the right convention for their own market, but
     * they are not the same measurement, and a banner showing them side by
     * side should be able to say which is which.
     */
    private String changeBasis;

    private LocalDateTime fetchedAt;
}
