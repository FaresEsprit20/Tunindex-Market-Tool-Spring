package com.tunindex.market_tool.api.dto.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * API-facing mirror of the collector's CorrelationPairDto.
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
public class CorrelationPairResponseDto {

    private String symbolA;
    private String symbolB;
    private BigDecimal correlation;
    private int overlap;
}
