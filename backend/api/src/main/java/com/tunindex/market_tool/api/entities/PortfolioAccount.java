package com.tunindex.market_tool.api.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One per user — a simulated (paper-trading) cash account. Positions are
 * built/unwound only against real, server-fetched prices at execution time
 * (see PortfolioService), never a client-supplied price.
 */
@Entity
@Table(name = "portfolio_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioAccount {

    public static final BigDecimal STARTING_CASH = BigDecimal.valueOf(20000);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "cash_balance", nullable = false)
    private BigDecimal cashBalance;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.cashBalance == null) {
            this.cashBalance = STARTING_CASH;
        }
    }
}
