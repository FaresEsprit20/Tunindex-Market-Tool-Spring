package com.tunindex.market_tool.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
        })
@AutoConfigureMockMvc
@EnableJpaRepositories(basePackages = "com.tunindex.market_tool.collector.repository")
@ComponentScan(basePackages = {
        "com.tunindex.market_tool.common.controllers",
        "com.tunindex.market_tool.common.services",
        "com.tunindex.market_tool.common"
})
@ActiveProfiles("test")
public abstract class BaseIntegrationTestConfig {

    @Autowired
    protected MockMvc mockMvc;

}