package com.tunindex.market_tool.collector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

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
 *
 * <p><b>WebTestClient, not MockMvc.</b> This service is built on WebFlux, and
 * MockMvc only exists for the servlet stack — {@code @AutoConfigureMockMvc}
 * contributes no bean here at all, so every test that asked for one failed to
 * start with "No qualifying bean of type MockMvc". Switching the web
 * environment around does not help either; there is no servlet
 * {@code DispatcherServlet} to drive in the first place. WebTestClient is the
 * reactive equivalent and binds straight to the application context, so these
 * stay in-process rather than needing a real port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "eureka.client.enabled=false",
                "spring.cloud.discovery.enabled=false"
        })
@AutoConfigureWebTestClient(timeout = "PT60S")
@ActiveProfiles("test")
public abstract class BaseIntegrationTestConfig {

    /**
     * Bound to the context, so requests never leave the JVM.
     *
     * <p>The timeout above is generous because one of these endpoints can
     * trigger a refresh that talks to a provider; the default five seconds
     * turned that into a flaky failure that looked like a broken endpoint
     * rather than a slow one.
     */
    protected WebTestClient webTestClient;

    /**
     * Attaches the internal API key that every {@code /internal/**} endpoint
     * checks.
     *
     * <p>Done once here rather than on each call, because forgetting it does
     * not look like a missing header - the controller answers 401, and a test
     * asserting 200 reports it as a broken endpoint. Setting it as a default
     * keeps the tests about the behaviour they are actually checking.
     */
    @Autowired
    void configureClient(WebTestClient client,
                         @Value("${internal.api.key}") String internalApiKey) {
        this.webTestClient = client.mutate()
                .defaultHeader("X-API-Key", internalApiKey)
                .build();
    }

}
