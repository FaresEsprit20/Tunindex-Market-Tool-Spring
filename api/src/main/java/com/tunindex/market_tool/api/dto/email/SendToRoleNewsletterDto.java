package com.tunindex.market_tool.api.dto.email;

import com.tunindex.market_tool.api.entities.enums.UserRole;
import lombok.Data;

@Data
public class SendToRoleNewsletterDto {

    private UserRole role;
    private String subject;
    private String content;
    private String label;

}