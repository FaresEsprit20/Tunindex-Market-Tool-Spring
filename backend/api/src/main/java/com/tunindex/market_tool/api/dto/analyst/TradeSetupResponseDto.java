package com.tunindex.market_tool.api.dto.analyst;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * The Tradify Analyst's entry plan for one stock, as the browser sees it.
 *
 * <p>Mirrors the collector's {@code TradeSetupDto}. Kept as a separate type
 * rather than shared, in line with every other DTO crossing this boundary: the
 * public shape is allowed to lag the internal one without the two services
 * having to deploy together.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// A field added upstream should not break the proxy before this class knows
// about it.
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeSetupResponseDto {

    private String symbol;

    /** ACCUMULATE_NOW | BUY_THE_DIP | WAIT_FOR_CONFIRMATION | HOLD_OFF | NO_SETUP */
    private String stance;

    /** One actionable line, e.g. "Accumulate 3.20 - 3.48". */
    private String headline;

    private BigDecimal buyZoneLow;
    private BigDecimal buyZoneHigh;
    private boolean priceInBuyZone;
    private BigDecimal distanceToZonePct;
    private String buyZoneBasis;

    private BigDecimal target1;
    private BigDecimal target2;
    private BigDecimal stopLevel;
    private BigDecimal expectedReturnPct;
    private BigDecimal riskReward;

    private String phase;
    private String regimeSummary;
    private List<String> evidence;
    private List<String> risks;
    private int confidence;
}
