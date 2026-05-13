package com.tunindex.market_tool.payment.controller.user_subscription;

import com.tunindex.market_tool.payment.dto.PaymentMethodType;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentStatusResponseDto {
    private String transactionId;
    private String providerPaymentId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private PaymentMethodType paymentMethod;
    private LocalDateTime paymentDate;
    private String failureReason;
}