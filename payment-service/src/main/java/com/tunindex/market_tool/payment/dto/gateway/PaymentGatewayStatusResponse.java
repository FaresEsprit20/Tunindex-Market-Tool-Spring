package com.tunindex.market_tool.payment.dto.gateway;

import com.tunindex.market_tool.payment.dto.PaymentMethodType;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentGatewayStatusResponse {
    private String providerPaymentId;
    private String transactionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private PaymentMethodType paymentMethod;
    private LocalDateTime paymentDate;
    private String failureReason;
}