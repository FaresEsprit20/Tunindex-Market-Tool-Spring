// SendToAllNewsletterDto.java
package com.tunindex.market_tool.mailing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendToAllNewsletterDto {
    @NotBlank
    private String subject;
    @NotBlank
    private String content;
    @NotBlank
    private String label;
}