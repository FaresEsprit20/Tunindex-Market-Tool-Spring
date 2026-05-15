package com.tunindex.market_tool.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestDto {
    private Long transactionId;
    private BigDecimal amount;
    private String reason;
}