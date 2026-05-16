package com.tunindex.market_tool.user_subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.tunindex.market_tool.user_subscription",
        "com.tunindex.market_tool.common"
})
@EntityScan(basePackages = {
        "com.tunindex.market_tool.user_subscription.entities",
        "com.tunindex.market_tool.common.entities"
})
@EnableJpaRepositories(basePackages = {
        "com.tunindex.market_tool.user_subscription.repository"
})
@EnableScheduling
@EnableDiscoveryClient
@Slf4j
public class UserSubscriptionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserSubscriptionServiceApplication.class, args);

        log.info("=".repeat(60));
        log.info("💳 User Subscription SERVICE STARTED");
        log.info("📡 Web server running on http://localhost:8089");
        log.info("📖 Swagger UI: http://localhost:8089/swagger-ui.html");
        log.info("=".repeat(60));
    }

}