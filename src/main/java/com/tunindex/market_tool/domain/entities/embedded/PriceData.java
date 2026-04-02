package com.tunindex.market_tool.domain.entities.embedded;

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
    private BigDecimal open;
    private BigDecimal dayHigh;
    private BigDecimal dayLow;
    private BigDecimal change;
    private BigDecimal changePct;

    // 52 Week Range
    private BigDecimal week52High;
    private BigDecimal week52Low;
    private String week52Range;
    private BigDecimal closeTo52weekslowPct;

    // Bid/Ask
    private BigDecimal bid;
    private BigDecimal ask;

    // Timestamp from source
    private Long lastUpdateTimestamp;
}