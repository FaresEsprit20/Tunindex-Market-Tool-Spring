package com.tunindex.market_tool.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.tunindex.market_tool.payment",
        "com.tunindex.market_tool.common"
})
@EntityScan(basePackages = {
        "com.tunindex.market_tool.payment.entities",
        "com.tunindex.market_tool.common.entities"
})
@EnableJpaRepositories(basePackages = {
        "com.tunindex.market_tool.payment.repository"
})
@EnableScheduling
@EnableDiscoveryClient
@Slf4j
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);

        log.info("=".repeat(60));
        log.info("💳 Billing SERVICE STARTED");
        log.info("📡 Web server running on http://localhost:8088");
        log.info("📖 Swagger UI: http://localhost:8088/swagger-ui.html");
        log.info("=".repeat(60));
    }

}