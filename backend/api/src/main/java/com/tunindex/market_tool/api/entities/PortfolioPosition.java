package com.tunindex.market_tool.api.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "portfolio_positions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "symbol"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private PortfolioAccount account;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false)
    private BigDecimal quantity;

    /** Weighted-average cost basis per share across all buys, in TND. */
    @Column(name = "avg_cost_basis", nullable = false)
    private BigDecimal avgCostBasis;
}
