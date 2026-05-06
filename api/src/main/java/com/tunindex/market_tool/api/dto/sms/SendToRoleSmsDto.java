package com.tunindex.market_tool.api.dto.sms;

import com.tunindex.market_tool.api.entities.enums.UserRole;
import lombok.Data;

@Data
public class SendToRoleSmsDto {

    private UserRole role;
    private String message;

}
