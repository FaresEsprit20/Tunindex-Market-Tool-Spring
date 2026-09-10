package com.tunindex.market_tool.api.dto.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioPositionDto {
    private String symbol;
    private String name;
    private BigDecimal quantity;
    private BigDecimal avgCostBasis;
    private BigDecimal currentPrice;
    private BigDecimal marketValue;
    private BigDecimal unrealizedPnl;
    private BigDecimal unrealizedPnlPct;

    /** Previous session's close, from the same live quote as currentPrice. */
    private BigDecimal prevClose;
    /** Today's move on this position, in TND and percent. */
    private BigDecimal dayChangeValue;
    private BigDecimal dayChangePct;

    /**
     * Shares eligible for today's change - those held through yesterday's
     * close, so excluding anything bought today.
     *
     * <p>Exposed so the client can recompute the day's move against a live
     * streamed price without having to guess this quantity. Recomputing with
     * the full holding would silently undo the rule: shares bought this
     * morning were not exposed to the move from yesterday's close, and
     * counting them shows a gain the holder never earned.
     */
    private BigDecimal dayChangeQuantity;
}
