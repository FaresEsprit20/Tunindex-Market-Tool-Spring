package com.tunindex.market_tool.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {
    private String currency;
    private String successRedirectUrl;
    private String cancelRedirectUrl;
    private String webhookUrl;
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private List<String> supportedCurrencies;
    private List<String> supportedPaymentMethods;
    private Integer paymentTimeoutMinutes;
}