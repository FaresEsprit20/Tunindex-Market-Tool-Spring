package com.tunindex.market_tool.collector.dto.news;

import com.tunindex.market_tool.collector.entities.StockNews;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockNewsDto {
    private String headline;
    private String url;
    private LocalDateTime publishedAt;

    public static StockNewsDto fromEntity(StockNews entity) {
        return StockNewsDto.builder()
                .headline(entity.getHeadline())
                .url(entity.getUrl())
                .publishedAt(entity.getPublishedAt())
                .build();
    }
}
