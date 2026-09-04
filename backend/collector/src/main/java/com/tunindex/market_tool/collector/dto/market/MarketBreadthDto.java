package com.tunindex.market_tool.collector.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Whole-market summary computed from the stored quotes: how many names rose,
 * how many fell, which moved most, and how each sector fared.
 *
 * <p>Every figure here is derived from {@code lastPrice} vs {@code prevClose}
 * on the stocks we actually hold — nothing is estimated or carried over from a
 * previous run. A stock missing either price is counted in {@code notPriced}
 * rather than silently folded into "unchanged", so the client can tell a flat
 * market from a thin one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketBreadthDto {

    private int advancing;
    private int declining;
    private int unchanged;

    /** Names we could not compute a move for (no last price, or no previous close). */
    private int notPriced;

    /** advancing + declining + unchanged + notPriced — every stock we track. */
    private int total;

    /** Mean day change across priced names only, in percent. */
    private BigDecimal averageChangePct;

    /** Sum of volume across priced names — a session-activity proxy, not turnover. */
    private Long totalVolume;

    private List<MarketMoverDto> topGainers;
    private List<MarketMoverDto> topLosers;
    private List<MarketMoverDto> mostActive;

    private List<SectorPerformanceDto> sectorPerformance;

    /** Most recent {@code lastUpdate} across the priced names — how fresh this is. */
    private LocalDateTime asOf;
}
