package com.tunindex.market_tool.collector;

import com.tunindex.market_tool.collector.services.status.PipelineRunnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.tunindex.market_tool.collector",
        "com.tunindex.market_tool.common"
})
@Slf4j
@EnableScheduling
@RequiredArgsConstructor
@EnableDiscoveryClient
@EntityScan("com.tunindex.market_tool.collector.entities")
@EnableJpaRepositories("com.tunindex.market_tool.collector.repository")
public class CollectorApplication {

    private final PipelineRunnerService pipelineRunnerService;

    @Value("${market-tool.scheduler.run-on-startup:false}")
    private boolean runOnStartup;

    @Value("${market-tool.parallelism.max-workers:10}")
    private int maxWorkers;

    @Value("${market-tool.scheduler.interval-minutes:30}")
    private int schedulerIntervalMinutes;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(CollectorApplication.class, args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("⏹️ Collector Service shutting down...");
            context.close();
        }, "shutdown-hook"));
    }

    @Bean
    public CommandLineRunner startBackgroundPipeline() {
        return args -> {
            log.info("=".repeat(60));
            log.info("📊 COLLECTOR SERVICE STARTED");
            log.info("⚙️  Max save workers: {}", maxWorkers);
            log.info("⚙️  Scheduler interval: {} minutes", schedulerIntervalMinutes);
            log.info("=".repeat(60));

            if (runOnStartup) {
                log.info("🚀 run-on-startup enabled — triggering initial pipeline run");
                pipelineRunnerService.triggerRun();
            } else {
                log.info("⏸️ Collector is idle. Waiting for a manual trigger via POST /internal/pipeline/start.");
            }
        };
    }
}