package com.tunindex.market_tool.api.dto.portfolio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Projected dividend income from one holding.
 *
 * <p>A projection, not a schedule: it applies the currently published yield to
 * the position at today's price. It assumes the payout is maintained, which is
 * exactly the assumption that fails when a company is in trouble — the UI
 * labels this as an estimate for that reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortfolioIncomeDto {

    private String symbol;
    private String name;
    private BigDecimal quantity;
    private BigDecimal marketValue;

    /** Published dividend yield in percent; null when we do not have one. */
    private BigDecimal dividendYieldPct;

    /** marketValue x yield, in TND per year. Null when the yield is unknown. */
    private BigDecimal projectedAnnualIncome;
}
