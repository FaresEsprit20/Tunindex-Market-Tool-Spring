package com.tunindex.market_tool.collector.config;

import com.tunindex.market_tool.collector.services.status.PipelineRunnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Startup banner, and the optional initial pipeline run.
 *
 * <p>This lived on {@code CollectorApplication} itself, which
 * constructor-injected {@link PipelineRunnerService}. That made every sliced
 * test in the module impossible: {@code @DataJpaTest} and friends deliberately
 * exclude {@code @Service} beans, but they still have to instantiate the
 * {@code @SpringBootConfiguration} class they find — so the context failed
 * with "required a bean of type PipelineRunnerService", and 41 integration
 * tests errored before running a single assertion.
 *
 * <p>Keeping the application class dependency-free is the general rule here:
 * it is a configuration entry point, not a place for wiring.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollectorStartupRunner implements CommandLineRunner {

    private final PipelineRunnerService pipelineRunnerService;

    @Value("${market-tool.scheduler.run-on-startup:false}")
    private boolean runOnStartup;

    @Value("${market-tool.parallelism.max-workers:10}")
    private int maxWorkers;

    @Value("${market-tool.scheduler.interval-minutes:30}")
    private int schedulerIntervalMinutes;

    @Override
    public void run(String... args) {
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
    }
}
