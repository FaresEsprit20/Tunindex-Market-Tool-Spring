package com.tunindex.market_tool.collector.dto.exchangerate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRateResponseDto {
    private String baseCurrency;
    private List<CurrencyRateDto> rates;
    private LocalDateTime lastUpdated;
}
