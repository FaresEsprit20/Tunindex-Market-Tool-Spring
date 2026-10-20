package com.tunindex.market_tool.api.dto.portfolio;

import com.tunindex.market_tool.api.entities.enums.TransactionSide;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioTransactionDto {
    private Long id;
    private String symbol;
    private TransactionSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalAmount;
    private BigDecimal realizedPnl;
    private LocalDateTime executedAt;
}
