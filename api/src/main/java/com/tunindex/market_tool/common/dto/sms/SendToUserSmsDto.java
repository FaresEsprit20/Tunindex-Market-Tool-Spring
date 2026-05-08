package com.tunindex.market_tool.common.dto.sms;

import lombok.Data;

@Data
public class SendToUserSmsDto {

    private String phoneNumber;
    private String message;

}