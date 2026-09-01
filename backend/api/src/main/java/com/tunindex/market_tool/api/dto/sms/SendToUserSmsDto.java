package com.tunindex.market_tool.api.dto.sms;

import lombok.Data;

@Data
public class SendToUserSmsDto {

    private String phoneNumber;
    private String message;

}