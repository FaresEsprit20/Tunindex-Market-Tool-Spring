package com.tunindex.market_tool.api.dto.news;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsImpactResponseDto {
    private String headline;
    private String url;
    private LocalDateTime publishedAt;

    private String sentiment;
    private List<String> matchedKeywords;

    private LocalDate priceBeforeDate;
    private BigDecimal priceBeforeClose;
    private LocalDate priceAfterDate;
    private BigDecimal priceAfterClose;
    private BigDecimal priceChangePct;
    private Boolean priceMoveMatchesSentiment;
}
