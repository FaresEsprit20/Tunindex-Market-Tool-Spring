package com.tunindex.market_tool.common.dto.providers.investingcom;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class DateRangeRequest {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    public boolean isValid() {
        return startDate != null && endDate != null && !startDate.isAfter(endDate);
    }
}