// SendToUserNewsletterDto.java
package com.tunindex.market_tool.mailing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendToUserNewsletterDto {
    @Email
    @NotBlank
    private String email;
    @NotBlank
    private String subject;
    @NotBlank
    private String content;
    @NotBlank
    private String label;
}