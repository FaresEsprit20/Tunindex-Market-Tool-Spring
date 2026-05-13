package com.tunindex.market_tool.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String invoiceNumber;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private Long transactionId;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    private String currency;
    
    private BigDecimal taxAmount;
    
    private BigDecimal totalAmount;
    
    private String status;
    
    private String pdfUrl;
    
    private LocalDateTime issueDate;
    
    private LocalDateTime dueDate;
    
    private LocalDateTime paidAt;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        issueDate = LocalDateTime.now();
        dueDate = LocalDateTime.now().plusDays(30);
        if (totalAmount == null) totalAmount = amount;
    }
}
