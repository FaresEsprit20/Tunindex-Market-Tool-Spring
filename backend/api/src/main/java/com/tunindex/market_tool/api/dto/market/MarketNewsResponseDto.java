package com.tunindex.market_tool.api.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Passthrough of the collector's MarketNewsDto. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketNewsResponseDto {
    private String headline;
    private String url;
    private LocalDateTime publishedAt;
    private BigDecimal relatedPrice;
    private BigDecimal relatedChangePct;
    private String sentiment;
}
