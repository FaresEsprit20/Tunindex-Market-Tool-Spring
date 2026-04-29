package com.tunindex.market_tool.domain.entities.embedded;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalculatedValues {

    @JsonProperty("graham_fair_value")
    private BigDecimal grahamFairValue;

    @JsonProperty("margin_of_safety")
    private BigDecimal marginOfSafety;

    @JsonProperty("book_value_per_share")
    private BigDecimal bookValuePerShare;
}