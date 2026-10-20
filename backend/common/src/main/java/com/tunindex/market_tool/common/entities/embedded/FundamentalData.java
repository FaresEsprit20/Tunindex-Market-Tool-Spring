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
public class FundamentalData {

    private BigDecimal marketCap;
    private Long sharesOutstanding;
    private BigDecimal eps;
    private BigDecimal peRatio;
    private BigDecimal dividendYield;

    /**
     * Share of earnings paid out as dividends, in percent.
     *
     * <p>Scraped and parsed since the beginning but never stored, because no
     * column existed to store it in - the value reached the DTO and was
     * dropped at the entity boundary.
     */
    private BigDecimal payoutRatio;

    private BigDecimal revenue;
    private BigDecimal oneYearReturn;

}