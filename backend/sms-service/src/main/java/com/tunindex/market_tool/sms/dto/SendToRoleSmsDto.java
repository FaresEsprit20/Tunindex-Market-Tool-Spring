package com.tunindex.market_tool.sms.dto;

import lombok.Data;

@Data
public class SendToRoleSmsDto {

    private UserRole role;
    private String message;

}
