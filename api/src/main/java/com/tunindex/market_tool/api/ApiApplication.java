package com.tunindex.market_tool.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@Slf4j
@EntityScan("com.tunindex.market_tool.common.entities")
@EnableJpaRepositories("com.tunindex.market_tool.common.repository")
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);

        log.info("=".repeat(60));
        log.info("🚀 API SERVICE STARTED");
        log.info("📡 Web server running on http://localhost:8080");
        log.info("📖 Swagger UI: http://localhost:8080/swagger-ui.html");
        log.info("=".repeat(60));
    }
}