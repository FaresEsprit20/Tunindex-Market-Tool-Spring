package com.tunindex.market_tool.core.scheduler;

import com.tunindex.market_tool.domain.services.orchestrator.DataOrchestrator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSchedulerImpl implements DataScheduler {

    private final DataOrchestrator orchestrator;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private static final int DEFAULT_INTERVAL_MINUTES = 30;

    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "data-scheduler");
            t.setDaemon(true);
            return t;
        });
        log.info("Scheduler initialized");
    }

    @PreDestroy
    public void destroy() {
        stop();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Scheduler destroyed");
    }

    @Override
    public void start() {
        start(DEFAULT_INTERVAL_MINUTES);
    }

    @Override
    public void start(int intervalMinutes) {
        if (running.get()) {
            log.warn("Scheduler is already running");
            return;
        }

        log.info("⏱️ Scheduler started (every {} min)", intervalMinutes);

        scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("🔄 Scheduler: Running pipeline...");
                runPipelineWithLogging();
            } catch (Exception e) {
                log.error("❌ Scheduler: Pipeline execution failed: {}", e.getMessage(), e);
            }
        }, 0, intervalMinutes, TimeUnit.MINUTES);

        running.set(true);
    }

    @Override
    public void stop() {
        if (!running.get()) {
            log.debug("Scheduler is not running");
            return;
        }

        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
        }

        running.set(false);
        log.info("⏹️ Scheduler stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void runOnce() {
        log.info("🔄 Running pipeline once...");
        runPipelineWithLogging();
    }

    /**
     * Run pipeline with logging and error handling
     * Matches Python's run_pipeline() behavior
     */
    private void runPipelineWithLogging() {
        try {
            // Run pipeline synchronously (block until complete)
            orchestrator.runPipeline()
                    .doOnSuccess(v -> log.info("✅ Pipeline completed successfully"))
                    .doOnError(e -> log.error("❌ Pipeline failed: {}", e.getMessage()))
                    .block(); // Block to match Python's synchronous behavior
        } catch (Exception e) {
            log.error("Pipeline execution error: {}", e.getMessage(), e);
        }
    }
}