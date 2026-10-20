package com.tunindex.market_tool.collector.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A market-wide headline from ilboursa's exchange news page — the feed
 * covering the whole BVMT rather than one symbol. Each row also carries
 * the price and day move the site filed the story against, when present.
 * url is unique, so re-scraping never duplicates a story.
 */
@Entity
@Table(name = "market_news", uniqueConstraints = @UniqueConstraint(columnNames = {"url"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String headline;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "related_price", precision = 19, scale = 4)
    private BigDecimal relatedPrice;

    @Column(name = "related_change_pct", precision = 19, scale = 4)
    private BigDecimal relatedChangePct;

    /** Rule-based tag from NewsSentimentClassifier — POSITIVE | NEGATIVE | NEUTRAL. */
    @Column(length = 16)
    private String sentiment;

    @Column(name = "scraped_at", updatable = false)
    private LocalDateTime scrapedAt;

    @PrePersist
    protected void onCreate() {
        this.scrapedAt = LocalDateTime.now();
    }
}
