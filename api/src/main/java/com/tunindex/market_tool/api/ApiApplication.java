package com.tunindex.market_tool.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
        "com.tunindex.market_tool.api",
        "com.tunindex.market_tool.common"
})
@Slf4j
@EnableJpaAuditing
@EnableDiscoveryClient
@EntityScan("com.tunindex.market_tool.api.entities")  // ← Only API entities
@EnableJpaRepositories("com.tunindex.market_tool.api.repository")  // ← Only API repositories
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);

        log.info("=".repeat(60));
        log.info("🚀 API SERVICE STARTED");
        log.info("📡 Web server running on http://localhost:8082");
        log.info("📖 Swagger UI: http://localhost:8082/swagger-ui.html");
        log.info("=".repeat(60));
    }
}