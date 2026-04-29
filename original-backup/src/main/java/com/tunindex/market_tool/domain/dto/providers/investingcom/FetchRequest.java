package com.tunindex.market_tool.domain.dto.providers.investingcom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class FetchRequest {

    @NotBlank(message = "Symbol is required")
    @Pattern(regexp = "^[A-Z]+$", message = "Symbol must contain only uppercase letters")
    private String symbol;

    private boolean forceRefresh = false;
}