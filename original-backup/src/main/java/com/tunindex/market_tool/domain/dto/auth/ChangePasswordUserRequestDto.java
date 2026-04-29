package com.tunindex.market_tool.domain.dto.auth;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChangePasswordUserRequestDto {

    private Integer id;
    private String password;
    private String confirmPassword;
    private String token;

}
