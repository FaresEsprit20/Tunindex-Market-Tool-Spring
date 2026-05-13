package com.tunindex.market_tool.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "konnect")
public class KonnectConfig {
    private String apiUrl;
    private String apiKey;
    private String webhookSecret;
    private String walletId;
    private String environment;
    private Integer timeout;
    private Integer retryAttempts;
}