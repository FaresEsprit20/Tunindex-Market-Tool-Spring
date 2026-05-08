package com.tunindex.market_tool.common.dto.recpatcha;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class RecaptchaRequestDto {

    String secret;
    String response;

}