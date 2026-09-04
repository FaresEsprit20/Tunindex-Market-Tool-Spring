package com.tunindex.market_tool.api.dto.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * API-facing mirror of the collector's CorrelationMatrixDto.
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
public class CorrelationMatrixResponseDto {

    /** Row and column order for {@link #matrix}. */
    private List<String> symbols;

    /** matrix[i][j] correlates symbols[i] with symbols[j]; null when too thin. */
    private List<List<BigDecimal>> matrix;

    private List<List<Integer>> overlap;
    private int windowDays;
    private int minOverlap;

    private List<CorrelationPairResponseDto> mostDiversifying;
    private List<CorrelationPairResponseDto> mostRedundant;
}
