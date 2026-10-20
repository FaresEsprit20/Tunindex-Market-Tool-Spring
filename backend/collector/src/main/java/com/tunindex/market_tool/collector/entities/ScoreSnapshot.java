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
 * One symbol's Tunindex Score on one day.
 *
 * <p>The scorer otherwise computes on request and throws the result away,
 * which makes two things impossible: showing whether a score is improving,
 * and ever checking whether high scores were followed by gains. This is the
 * record that makes both answerable — going forward, honestly, without
 * reconstructing anything.
 *
 * <p>Unique on (symbol, snapshotDate) so a re-run on the same day updates
 * rather than duplicating.
 */
@Entity
@Table(name = "score_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "snapshot_date"}),
        indexes = @Index(name = "idx_score_snapshots_symbol_date", columnList = "symbol, snapshot_date"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoreSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(length = 16)
    private String verdict;

    @Column(name = "valuation_score")
    private Integer valuationScore;

    @Column(name = "timing_score")
    private Integer timingScore;

    @Column(name = "financial_health_score")
    private Integer financialHealthScore;

    @Column(name = "income_score")
    private Integer incomeScore;

    @Column(name = "momentum_score")
    private Integer momentumScore;

    @Column(name = "news_score")
    private Integer newsScore;

    @Column(name = "data_completeness")
    private int dataCompleteness;

    /** The close on the snapshot date — the entry price a forward return measures from. */
    @Column(name = "close_price", precision = 19, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
