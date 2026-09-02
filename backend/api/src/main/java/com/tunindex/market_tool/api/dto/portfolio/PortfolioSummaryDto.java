package com.tunindex.market_tool.api.dto.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSummaryDto {
    private BigDecimal cashBalance;
    private BigDecimal startingCash;
    private List<PortfolioPositionDto> positions;
    private BigDecimal totalMarketValue;
    private BigDecimal totalPortfolioValue;
    private BigDecimal totalUnrealizedPnl;
    private BigDecimal totalUnrealizedPnlPct;
    private BigDecimal totalRealizedPnl;
    private BigDecimal totalReturnPct;
}
