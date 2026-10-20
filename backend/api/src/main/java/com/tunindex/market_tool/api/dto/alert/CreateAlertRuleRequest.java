package com.tunindex.market_tool.api.dto.alert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateAlertRuleRequest {

    @NotBlank(message = "Symbol is required")
    private String symbol;

    @NotNull(message = "Alert type is required")
    private String type;

    /** Required for threshold types; ignored for event types. */
    private BigDecimal threshold;
}
