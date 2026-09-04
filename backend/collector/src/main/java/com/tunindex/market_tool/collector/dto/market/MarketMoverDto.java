package com.tunindex.market_tool.collector.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One name in a movers list, carrying just enough to render a row. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketMoverDto {

    private String symbol;
    private String name;
    private String sector;
    private String exchange;
    private BigDecimal lastPrice;
    private BigDecimal prevClose;
    private BigDecimal changePct;
    private Long volume;
}
