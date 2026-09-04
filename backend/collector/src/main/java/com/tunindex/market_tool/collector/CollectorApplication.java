package com.tunindex.market_tool.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the collector service.
 *
 * <p>Deliberately holds no injected dependencies. Spring's sliced test
 * annotations ({@code @DataJpaTest}, {@code @WebMvcTest}) still instantiate
 * the {@code @SpringBootConfiguration} class they discover, while excluding
 * most bean types — so a constructor argument here breaks every sliced test in
 * the module with a "required a bean" failure that has nothing to do with the
 * test. Startup work belongs in {@code CollectorStartupRunner}.
 */
@SpringBootApplication(scanBasePackages = {
        "com.tunindex.market_tool.collector",
        "com.tunindex.market_tool.common"
})
@Slf4j
@EnableScheduling
@EnableDiscoveryClient
@EntityScan("com.tunindex.market_tool.collector.entities")
@EnableJpaRepositories("com.tunindex.market_tool.collector.repository")
public class CollectorApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(CollectorApplication.class, args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("⏹️ Collector Service shutting down...");
            context.close();
        }, "shutdown-hook"));
    }
}
