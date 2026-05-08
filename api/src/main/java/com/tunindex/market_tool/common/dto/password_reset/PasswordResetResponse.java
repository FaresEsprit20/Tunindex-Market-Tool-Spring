package com.tunindex.market_tool.common.dto.password_reset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class PasswordResetResponse {

    private String message;
    private int remainingTimeSeconds;

}
