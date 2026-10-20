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

    /**
     * When a live exchange quote was last successfully applied to this row.
     *
     * <p>Distinct from {@code Stock.lastUpdate}, which records when the row
     * was last written for any reason. A pipeline run that fails to reach the
     * exchange still writes the row — carrying forward the fundamentals
     * provider's price, which lags a full trading day on this market — and
     * stamps lastUpdate with the current time. Anything presenting a figure
     * as "today's" must therefore check this field, not lastUpdate: without
     * it a stale price wears a fresh timestamp and can lead the movers list.
     *
     * <p>Null means we have never obtained a live quote for this symbol.
     */
    private java.time.LocalDateTime liveQuoteAt;
}