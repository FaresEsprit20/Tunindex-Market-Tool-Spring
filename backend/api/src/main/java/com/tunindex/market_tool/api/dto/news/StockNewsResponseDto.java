package com.tunindex.market_tool.api.dto.news;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockNewsResponseDto {
    private String headline;
    private String url;
    private LocalDateTime publishedAt;
}
