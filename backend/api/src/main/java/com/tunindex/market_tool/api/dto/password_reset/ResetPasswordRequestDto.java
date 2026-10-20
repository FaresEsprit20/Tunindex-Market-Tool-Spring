package com.tunindex.market_tool.api.dto.password_reset;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ResetPasswordRequestDto {

    private String token;
    private String newPassword;

}