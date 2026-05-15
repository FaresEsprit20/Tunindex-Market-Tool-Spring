package com.tunindex.market_tool.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment")
public class CurrencyConfig {
    private List<String> supportedCurrencies;
    private String currency;
}