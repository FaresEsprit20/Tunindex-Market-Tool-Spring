package com.tunindex.market_tool.common.dto.sms;

import com.tunindex.market_tool.common.entities.enums.UserRole;
import lombok.Data;

@Data
public class SendToRoleSmsDto {

    private UserRole role;
    private String message;

}
