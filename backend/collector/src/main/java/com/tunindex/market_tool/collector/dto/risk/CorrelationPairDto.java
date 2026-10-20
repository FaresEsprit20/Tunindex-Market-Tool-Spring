package com.tunindex.market_tool.collector.dto.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One off-diagonal cell of the correlation matrix, flattened for ranking. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrelationPairDto {

    private String symbolA;
    private String symbolB;
    private BigDecimal correlation;
    private int overlap;
}
