package com.tunindex.market_tool.payment.entities;

import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long transactionId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    private String providerRefundId;

    private String failureReason;

    private LocalDateTime refundDate;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        refundDate = LocalDateTime.now();
        if (status == null) status = RefundStatus.PENDING;
    }
}