package com.tunindex.market_tool.collector.dto.news;

import com.tunindex.market_tool.collector.entities.MarketNews;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketNewsDto {

    private String headline;
    private String url;
    private LocalDateTime publishedAt;
    private BigDecimal relatedPrice;
    private BigDecimal relatedChangePct;
    private String sentiment;

    public static MarketNewsDto fromEntity(MarketNews entity) {
        return MarketNewsDto.builder()
                .headline(entity.getHeadline())
                .url(entity.getUrl())
                .publishedAt(entity.getPublishedAt())
                .relatedPrice(entity.getRelatedPrice())
                .relatedChangePct(entity.getRelatedChangePct())
                .sentiment(entity.getSentiment())
                .build();
    }
}
