package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {
    private Long id;
    private String invoiceNumber;
    private Long userId;
    private Long transactionId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private InvoiceStatus status;
    private String pdfUrl;
    private LocalDateTime issueDate;
    private LocalDateTime dueDate;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}