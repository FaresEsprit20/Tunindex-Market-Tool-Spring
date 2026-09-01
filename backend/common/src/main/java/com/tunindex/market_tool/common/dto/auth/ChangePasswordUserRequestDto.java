package com.tunindex.market_tool.common.dto.auth;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordUserRequestDto {

    private Integer id;
    private String password;
    private String confirmPassword;
    private String token;

}
