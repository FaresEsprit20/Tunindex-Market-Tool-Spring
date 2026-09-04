package com.tunindex.market_tool.api.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API-facing mirror of the collector's SectorPerformanceDto.
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
public class SectorPerformanceResponseDto {

    private String sector;

    /** Equal-weighted mean move; null when no name in the sector is priced. */
    private BigDecimal averageChangePct;

    private int advancing;
    private int declining;

    /** Sample size behind the average. */
    private int priced;

    private int total;
}
