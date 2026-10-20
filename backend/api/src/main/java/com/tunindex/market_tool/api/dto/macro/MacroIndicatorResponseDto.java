package com.tunindex.market_tool.api.dto.macro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One published Tunisian macro figure, with its provenance.
 *
 * <p>{@code periodLabel} is the publisher's own wording for the period the
 * figure applies to. It travels with the value because a rate quoted without
 * its period is misleading, not merely terse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MacroIndicatorResponseDto {

    /** POLICY_RATE | MONEY_MARKET_RATE | TMM | SAVINGS_RATE | INFLATION_CPI | GDP_GROWTH */
    private String key;

    private String label;

    /** What the figure means for an equity investor, in one sentence. */
    private String note;

    private BigDecimal value;
    private String unit;

    /** The period exactly as the publisher states it. */
    private String periodLabel;

    private String source;
    private String sourceUrl;
}
