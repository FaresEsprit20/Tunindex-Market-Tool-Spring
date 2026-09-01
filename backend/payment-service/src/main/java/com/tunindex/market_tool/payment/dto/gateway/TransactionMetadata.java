package com.tunindex.market_tool.payment.dto.gateway;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionMetadata {
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String customerEmail;
    private String customerName;
}