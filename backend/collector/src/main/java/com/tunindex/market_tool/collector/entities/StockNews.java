package com.tunindex.market_tool.collector.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One real news article for a symbol, scraped from ilboursa.com's
 * per-stock news feed (marches/news_valeur?s=SYMBOL) — genuine headlines
 * and publish timestamps from the site's own editorial feed, not
 * generated. url is unique per symbol so re-scraping the same page never
 * creates duplicate rows.
 */
@Entity
@Table(name = "stock_news",
        uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "url"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 500)
    private String headline;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "scraped_at", updatable = false)
    private LocalDateTime scrapedAt;

    @PrePersist
    protected void onCreate() {
        scrapedAt = LocalDateTime.now();
    }
}
