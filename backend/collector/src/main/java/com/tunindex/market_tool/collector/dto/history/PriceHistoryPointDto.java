package com.tunindex.market_tool.collector.dto.history;

import com.tunindex.market_tool.collector.entities.PriceHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistoryPointDto {

    private LocalDate tradeDate;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;

    public static PriceHistoryPointDto fromEntity(PriceHistory entity) {
        return PriceHistoryPointDto.builder()
                .tradeDate(entity.getTradeDate())
                .open(entity.getOpen())
                .high(entity.getHigh())
                .low(entity.getLow())
                .close(entity.getClose())
                .volume(entity.getVolume())
                .build();
    }
}
