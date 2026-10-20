package com.tunindex.market_tool.collector.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * How one sector did today. Equal-weighted on purpose: we have no reliable
 * free-float market cap for most BVMT names, and a cap-weighted figure built
 * on partial data would be worse than an honest average of the moves we do
 * have. {@code priced} says how many names that average rests on.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorPerformanceDto {

    private String sector;
    private BigDecimal averageChangePct;
    private int advancing;
    private int declining;

    /** Names in this sector with a computable move — the average's sample size. */
    private int priced;

    /** Names in this sector overall, priced or not. */
    private int total;
}
