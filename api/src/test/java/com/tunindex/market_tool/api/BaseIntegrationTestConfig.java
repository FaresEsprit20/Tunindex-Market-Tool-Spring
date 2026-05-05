package com.tunindex.market_tool.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
        })
@AutoConfigureMockMvc
@ComponentScan(basePackages = {
        "com.tunindex.market_tool.api.controllers",
        "com.tunindex.market_tool.api.services",
        "com.tunindex.market_tool.common"
})
@ActiveProfiles("test")
public abstract class BaseIntegrationTestConfig {

    @Autowired
    protected MockMvc mockMvc;

}