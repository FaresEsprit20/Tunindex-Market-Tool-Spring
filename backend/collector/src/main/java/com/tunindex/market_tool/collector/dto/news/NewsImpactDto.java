package com.tunindex.market_tool.collector.dto.news;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pairs one real news headline with (a) a transparent, rule-based sentiment
 * classification — see NewsSentimentClassifier, a fixed keyword list, not a
 * model — and (b) the REAL closing-price move around its publish date, from
 * the same scraped PriceHistory used everywhere else. priceMoveMatchesSentiment
 * is a simple fact ("did the price go the direction the keywords suggested"),
 * not a prediction or a claim of causation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsImpactDto {
    private String headline;
    private String url;
    private LocalDateTime publishedAt;

    private String sentiment; // POSITIVE | NEGATIVE | NEUTRAL
    private List<String> matchedKeywords;

    private java.time.LocalDate priceBeforeDate;
    private BigDecimal priceBeforeClose;
    private java.time.LocalDate priceAfterDate;
    private BigDecimal priceAfterClose;
    private BigDecimal priceChangePct;
    private Boolean priceMoveMatchesSentiment;
}
