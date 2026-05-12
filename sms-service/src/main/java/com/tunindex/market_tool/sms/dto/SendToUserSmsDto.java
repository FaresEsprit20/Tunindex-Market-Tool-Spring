package com.tunindex.market_tool.sms.dto;

import lombok.Data;

@Data
public class SendToUserSmsDto {

    private String phoneNumber;
    private String message;

}