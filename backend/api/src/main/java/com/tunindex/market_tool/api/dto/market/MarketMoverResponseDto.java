package com.tunindex.market_tool.api.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API-facing mirror of the collector's MarketMoverDto.
 *
 * <p>Deliberately a separate type rather than a shared one: the collector is
 * free to add internal fields without those leaking into the public contract,
 * and {@code @JsonIgnoreProperties} means a new field upstream cannot break
 * deserialisation here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketMoverResponseDto {

    private String symbol;
    private String name;
    private String sector;
    private String exchange;
    private BigDecimal lastPrice;
    private BigDecimal prevClose;
    private BigDecimal changePct;
    private Long volume;
}
