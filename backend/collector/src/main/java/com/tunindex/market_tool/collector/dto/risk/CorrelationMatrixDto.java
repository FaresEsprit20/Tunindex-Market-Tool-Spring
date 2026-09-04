package com.tunindex.market_tool.collector.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pairwise correlation of daily returns across a set of names.
 *
 * <p>The matrix is square and symmetric, indexed by {@link #symbols} in the
 * order given. A cell is null when the two names share too few overlapping
 * trading days to correlate — the UI renders those blank rather than as 0,
 * which would falsely read as "uncorrelated", the most useful answer there is.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationMatrixDto {

    /** Row and column order for {@link #matrix}. */
    private List<String> symbols;

    /** matrix[i][j] = Pearson correlation of symbols[i] and symbols[j], -1..1. */
    private List<List<BigDecimal>> matrix;

    /** Overlapping observation count behind each cell, same indexing. */
    private List<List<Integer>> overlap;

    /** Requested lookback in calendar days. */
    private int windowDays;

    /** Minimum overlapping days required before a cell is computed. */
    private int minOverlap;

    /** The least correlated pairs in the set — the diversification candidates. */
    private List<CorrelationPairDto> mostDiversifying;

    /** The most correlated pairs — effectively duplicate exposure. */
    private List<CorrelationPairDto> mostRedundant;
}
