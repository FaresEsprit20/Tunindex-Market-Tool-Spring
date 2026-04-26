package com.tunindex.market_tool.core.config.webclient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 5)) // 5MB buffer
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .defaultHeader("Accept-Encoding", "gzip, deflate, br")
                .defaultHeader("Connection", "keep-alive")
                .defaultHeader("Upgrade-Insecure-Requests", "1")
                .defaultHeader("Cache-Control", "max-age=0")
                .build();
    }
}