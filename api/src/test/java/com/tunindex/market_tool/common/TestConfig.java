package com.tunindex.market_tool.common;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication(scanBasePackages = "com.tunindex.market_tool.common")
@EntityScan("com.tunindex.market_tool.common.entities")
@EnableJpaRepositories("com.tunindex.market_tool.common.repository")
public class TestConfig {
    // This is a test configuration class
}