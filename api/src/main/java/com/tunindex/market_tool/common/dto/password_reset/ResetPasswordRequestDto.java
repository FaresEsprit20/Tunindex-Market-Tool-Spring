package com.tunindex.market_tool.common.dto.password_reset;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ResetPasswordRequestDto {

    private String token;
    private String newPassword;

}