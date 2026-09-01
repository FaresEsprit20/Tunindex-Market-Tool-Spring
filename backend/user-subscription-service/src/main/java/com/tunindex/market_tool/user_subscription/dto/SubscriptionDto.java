package com.tunindex.market_tool.user_subscription.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SubscriptionDto {
    private Long id;
    private Long userId;
    private String planName;
    private String status;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean autoRenew;
}