package com.tunindex.market_tool.recaptcha.dto;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RecaptchaRequestDto {

    String secret;
    String response;

}