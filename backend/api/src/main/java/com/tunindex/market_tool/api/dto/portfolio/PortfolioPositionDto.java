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
}
