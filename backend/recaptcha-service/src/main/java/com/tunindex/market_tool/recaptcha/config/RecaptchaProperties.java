package com.tunindex.market_tool.recaptcha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "google.recaptcha")
public class RecaptchaProperties {

    private String secret;
    private String url;
    private double scoreThreshold;

}