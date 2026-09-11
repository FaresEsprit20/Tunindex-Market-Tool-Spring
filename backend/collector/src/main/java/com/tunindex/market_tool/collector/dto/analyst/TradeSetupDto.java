package com.tunindex.market_tool.collector.dto.analyst;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * What a analyst would actually write down before placing a trade: where to
 * buy, where it goes wrong, and what it is worth if it goes right.
 *
 * <p>This is the piece a score alone never gave. "BUY, 74/100" tells somebody
 * nothing about <em>at what price</em> - and the price is the whole decision.
 * A stock can be an excellent business and a poor purchase today, and the only
 * thing separating those two readings is the level you pay.
 *
 * <p>Every field is null rather than guessed when the inputs are not there. A
 * fabricated entry level is worse than no level at all: somebody would trade on
 * it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeSetupDto {

    /**
     * What to do now, given where the price sits relative to the zone.
     *
     * <p>The distinction that matters most is between a stock worth owning and
     * a stock worth buying <em>at today's price</em>. Most "buy" ratings
     * collapse the two.
     */
    public enum Stance {
        /** Price is inside the entry band and the turn is confirmed. */
        ACCUMULATE_NOW,
        /** Worth owning, but not here - wait for the pullback. */
        BUY_THE_DIP,
        /** Basing, but the turn has not been confirmed yet. */
        WAIT_FOR_CONFIRMATION,
        /** Still falling, or extended. Buying here fights the trend. */
        HOLD_OFF,
        /** Not enough data to form a view. */
        NO_SETUP
    }

    private String symbol;
    private Stance stance;

    /** One line a reader can act on, e.g. "Accumulate 3.20 - 3.48". */
    private String headline;

    // ── Where to buy ─────────────────────────────────────────────────────

    /** Bottom of the entry band - the nearest real support below price. */
    private BigDecimal buyZoneLow;

    /** Top of the entry band - above this you are paying up. */
    private BigDecimal buyZoneHigh;

    /** Whether today's price is inside the band. */
    private boolean priceInBuyZone;

    /** How far today's price sits above the top of the band, in percent. */
    private BigDecimal distanceToZonePct;

    /** Plain-language account of what the band was derived from. */
    private String buyZoneBasis;

    // ── Where it goes ────────────────────────────────────────────────────

    /** First objective: the nearest resistance the move has to clear. */
    private BigDecimal target1;

    /** Second objective, usually the fundamental fair value. */
    private BigDecimal target2;

    /** Where the setup is wrong and the position should be cut. */
    private BigDecimal stopLevel;

    /** Return from the middle of the buy zone to the first target. */
    private BigDecimal expectedReturnPct;

    /** Reward against risk, both measured from the middle of the zone. */
    private BigDecimal riskReward;

    // ── Why ──────────────────────────────────────────────────────────────

    /** Where the stock sits in its cycle, from the reversal read. */
    private String phase;

    /**
     * The part a bare indicator reading misses: what the stock has been doing
     * for months, and what has changed.
     */
    private String regimeSummary;

    /** The checks that support the setup, in plain language. */
    private List<String> evidence;

    /** What would make this wrong, stated before the fact. */
    private List<String> risks;

    /** 0-100, how much of the analysis rests on data we actually have. */
    private int confidence;
}
