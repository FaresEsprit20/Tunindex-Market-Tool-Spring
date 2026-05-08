package com.tunindex.market_tool.common.dto.email;

import com.tunindex.market_tool.common.entities.enums.UserRole;
import lombok.Data;

@Data
public class SendToRoleNewsletterDto {

    private UserRole role;
    private String subject;
    private String content;
    private String label;

}