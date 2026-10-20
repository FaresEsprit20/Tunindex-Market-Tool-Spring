package com.tunindex.market_tool.api.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * API-facing mirror of the collector's MarketBreadthDto.
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
public class MarketBreadthResponseDto {

    private int advancing;
    private int declining;
    private int unchanged;

    /** Names with no computable move — missing last price or previous close. */
    private int notPriced;

    private int total;

    /** Mean day change across priced names only, in percent. */
    private BigDecimal averageChangePct;

    private Long totalVolume;

    private List<MarketMoverResponseDto> topGainers;
    private List<MarketMoverResponseDto> topLosers;
    private List<MarketMoverResponseDto> mostActive;

    private List<SectorPerformanceResponseDto> sectorPerformance;

    /** Freshest quote timestamp behind these figures. */
    private LocalDateTime asOf;
}
