package com.tunindex.market_tool.collector.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One name flagged as behaving unusually today, with the evidence attached.
 *
 * <p>Each signal carries the figures it was raised from, so the UI never has
 * to show a bare "unusual" badge — the user sees the multiple, the threshold
 * it cleared and what it was measured against, and can disagree.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnusualActivityDto {

    private String symbol;
    private String name;
    private String sector;

    /** VOLUME_SPIKE | BREAKOUT_52W_HIGH | BREAKDOWN_52W_LOW | LARGE_MOVE | WIDE_RANGE */
    private String signal;

    /** Short sentence naming the evidence, e.g. "Volume 4.2x its 3-month average". */
    private String detail;

    /**
     * How far past the threshold this went, normalised so signals of different
     * kinds can be ranked against each other. Higher is more unusual.
     */
    private BigDecimal strength;

    private BigDecimal lastPrice;
    private BigDecimal changePct;
    private Long volume;
    private Long avgVolume3m;

    /** Volume as a multiple of the 3-month average; null when we have no average. */
    private BigDecimal volumeMultiple;

    private BigDecimal week52High;
    private BigDecimal week52Low;
}
