package com.tunindex.market_tool.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared setup for the collector's integration tests.
 *
 * <p>Intentionally minimal. {@code @SpringBootTest} discovers
 * {@link CollectorApplication} on its own, and that class already declares the
 * component scan, entity scan and repository packages — so restating them here
 * is not merely redundant, it breaks the context: a second
 * {@code @EnableJpaRepositories} over the same package registers every
 * repository bean twice and the run fails with
 * {@code BeanDefinitionOverrideException}. A duplicated {@code @ComponentScan}
 * has the same character of problem, replacing the application's scan with a
 * narrower one.
 *
 * <p>The two properties below are the only real overrides: service discovery
 * is switched off because no Eureka server is running under test.
 */
// MOCK, not RANDOM_PORT: @AutoConfigureMockMvc only supplies a MockMvc bean
// in a mock web environment. Under RANDOM_PORT a real servlet container
// starts and there is no MockMvc to inject, which is what every one of these
// tests was failing on.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BaseIntegrationTestConfig {

    @Autowired
    protected MockMvc mockMvc;

}
