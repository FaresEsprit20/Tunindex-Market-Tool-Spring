package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodDto {
    private PaymentMethod method;
    private String displayName;
    private String iconUrl;
    private Boolean isActive;
    private String instructions;
}