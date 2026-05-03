package com.tunindex.market_tool.api.entities.embedded;

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
public class RatiosData {

    private BigDecimal priceToBook;
    private BigDecimal debtToEquity;
    private BigDecimal profitMargin;
}