package com.tunindex.market_tool.api.dto.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One slice of the book — a position or a sector — with its weight.
 * Used for both breakdowns so the UI can render them with one component.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioWeightDto {

    /** Symbol for a position row, sector name for a sector row. */
    private String key;

    /** Company name for a position row; null for a sector row. */
    private String label;

    private BigDecimal marketValue;

    /** Share of the invested book, in percent. Cash is excluded throughout. */
    private BigDecimal weightPct;

    /** Positions folded into this row — 1 for a position, n for a sector. */
    private int positions;
}
