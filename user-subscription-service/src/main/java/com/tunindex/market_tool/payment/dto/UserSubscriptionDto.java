package com.tunindex.market_tool.payment.dto;

import com.tunindex.market_tool.payment.entities.enums.BillingPeriod;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubscriptionDto {
    private Long id;
    private Long userId;
    private Long planId;
    private String planName;
    private SubscriptionStatus status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BillingPeriod billingPeriod;
    private Boolean autoRenew;
    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}