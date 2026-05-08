package com.tunindex.market_tool.common.dto.auth;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class AuthenticationRequest {

    private String login;

    private String password;

    @JsonProperty("recaptchaToken")
    private String recaptchaToken;

    @JsonProperty("remember_me")
    private Boolean rememberMe;

}