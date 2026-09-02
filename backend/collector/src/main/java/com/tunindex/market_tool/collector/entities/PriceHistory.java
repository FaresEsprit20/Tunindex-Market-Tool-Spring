package com.tunindex.market_tool.collector.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One real trading day's OHLCV for a symbol — scraped from ilboursa.com's
 * quote-download CSV (genuine daily open/high/low/close/volume, not
 * synthesized). Distinct from Stock (today's live snapshot): this is the
 * time series that makes an actual price chart possible.
 */
@Entity
@Table(name = "price_history",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "trade_date"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
