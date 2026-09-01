package com.tunindex.market_tool.common.entities.embedded;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceData {

    private BigDecimal lastPrice;
    private BigDecimal prevClose;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;


    // 52 Week Range
    private BigDecimal week52High;
    private BigDecimal week52Low;
    private String week52Range;
    private BigDecimal closeTo52weekslowPct;


    // Timestamp from source
    private Long lastUpdateTimestamp;
}