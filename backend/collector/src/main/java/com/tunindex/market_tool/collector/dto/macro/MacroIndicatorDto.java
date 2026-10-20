package com.tunindex.market_tool.collector.dto.macro;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One published macroeconomic figure for Tunisia.
 *
 * <p>Every field that describes provenance is mandatory in practice: these
 * numbers move slowly and are quoted for a specific period, so a rate shown
 * without the period it applies to is misleading rather than merely terse.
 * {@code periodLabel} carries the publisher's own wording ("au 03/09/2026",
 * "du mois de Août 2026") rather than a parsed date — the source states the
 * period unambiguously and re-deriving it in another locale only adds a way
 * to be wrong.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MacroIndicatorDto {

    /** POLICY_RATE | MONEY_MARKET_RATE | TMM | SAVINGS_RATE | INFLATION_CPI | GDP_GROWTH */
    private String key;

    /** Short English label for display. */
    private String label;

    /** What this figure means for an equity investor, in one sentence. */
    private String note;

    private BigDecimal value;

    /** Always "%" today, but carried so the UI never assumes. */
    private String unit;

    /** The period exactly as the publisher states it. */
    private String periodLabel;

    private String source;

    private String sourceUrl;
}
