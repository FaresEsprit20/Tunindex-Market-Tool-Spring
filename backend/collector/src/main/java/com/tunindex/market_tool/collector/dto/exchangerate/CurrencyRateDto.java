package com.tunindex.market_tool.collector.dto.exchangerate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyRateDto {
    private String code;
    private String name;
    /** How many TND for 1 unit of this currency. */
    private BigDecimal rateToTnd;
}
