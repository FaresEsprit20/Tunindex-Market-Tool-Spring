package com.tunindex.market_tool.api.entities;

import com.tunindex.market_tool.api.entities.enums.TransactionSide;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "portfolio_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private PortfolioAccount account;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionSide side;

    @Column(nullable = false)
    private BigDecimal quantity;

    /** The real, server-fetched price at execution time — never client-supplied. */
    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    /** Only meaningful for SELL rows: (price - avgCostBasisAtSaleTime) * quantity. */
    @Column(name = "realized_pnl")
    private BigDecimal realizedPnl;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    protected void onCreate() {
        this.executedAt = LocalDateTime.now();
    }
}
