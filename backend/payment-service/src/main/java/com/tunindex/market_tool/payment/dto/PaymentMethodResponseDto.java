package com.tunindex.market_tool.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentMethodResponseDto {
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private Boolean isActive;
    private Integer displayOrder;
}