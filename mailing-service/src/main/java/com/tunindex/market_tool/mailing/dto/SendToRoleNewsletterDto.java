// SendToRoleNewsletterDto.java
package com.tunindex.market_tool.mailing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendToRoleNewsletterDto {
    @NotNull
    private UserRole role;
    @NotBlank
    private String subject;
    @NotBlank
    private String content;
    @NotBlank
    private String label;
}