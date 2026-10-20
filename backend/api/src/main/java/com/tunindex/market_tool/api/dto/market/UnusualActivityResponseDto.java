package com.tunindex.market_tool.api.dto.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API-facing mirror of the collector's UnusualActivityDto.
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
public class UnusualActivityResponseDto {

    private String symbol;
    private String name;
    private String sector;

    /** VOLUME_SPIKE | BREAKOUT_52W_HIGH | BREAKDOWN_52W_LOW | LARGE_MOVE | WIDE_RANGE */
    private String signal;

    /** The evidence, in one sentence. */
    private String detail;

    /** Normalised so signals of different kinds rank against each other. */
    private BigDecimal strength;

    private BigDecimal lastPrice;
    private BigDecimal changePct;
    private Long volume;
    private Long avgVolume3m;
    private BigDecimal volumeMultiple;
    private BigDecimal week52High;
    private BigDecimal week52Low;
}
