package com.tunindex.market_tool;

import com.tunindex.market_tool.core.scheduler.DataScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
@Slf4j
public class MarketToolApplication {

    @Autowired
    private DataScheduler dataScheduler;

    @Value("${market-tool.scheduler.interval-minutes:30}")
    private int schedulerIntervalMinutes;

    @Value("${market-tool.scheduler.run-on-startup:true}")
    private boolean runOnStartup;

    @Value("${market-tool.scheduler.initial-delay-seconds:2}")
    private int initialDelaySeconds;

    @Value("${market-tool.parallelism.max-workers:10}")
    private int maxWorkers;

    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private static ExecutorService backgroundExecutor;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MarketToolApplication.class, args);

        // Add shutdown hook for graceful shutdown (matches Python KeyboardInterrupt)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isShuttingDown.compareAndSet(false, true)) {
                log.info("⏹️ Application shutting down...");

                // Shutdown background executor
                if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
                    backgroundExecutor.shutdown();
                    try {
                        if (!backgroundExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                            backgroundExecutor.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        backgroundExecutor.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }

                context.close();
            }
        }, "shutdown-hook"));
    }

    /**
     * Run pipeline in background with multithreading
     * Maps to Python: threading.Thread(target=start_pipeline, daemon=True).start()
     */
    @Bean
    public CommandLineRunner startBackgroundPipeline() {
        return args -> {
            log.info("=".repeat(60));
            log.info("🚀 Market Tool Application Started");
            log.info("📡 Web server running on http://localhost:8082");
            log.info("⚙️  Max workers: {}", maxWorkers);
            log.info("⚙️  Scheduler interval: {} minutes", schedulerIntervalMinutes);
            log.info("=".repeat(60));

            if (runOnStartup) {
                // Create daemon thread pool (matches Python daemon threads)
                backgroundExecutor = Executors.newFixedThreadPool(maxWorkers, new DaemonThreadFactory());

                // Submit pipeline task to executor (non-blocking)
                backgroundExecutor.submit(() -> {
                    try {
                        // Wait for application to fully start
                        Thread.sleep(initialDelaySeconds * 1000L);

                        log.info("🔄 Running initial pipeline on background thread...");
                        dataScheduler.runOnce();
                        log.info("✅ Initial pipeline completed successfully");

                        // Start the scheduled pipeline (runs in background)
                        startScheduledPipeline();

                    } catch (InterruptedException e) {
                        log.warn("Pipeline thread interrupted");
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("❌ Pipeline execution failed: {}", e.getMessage(), e);
                    }
                });

                log.info("🚀 Background pipeline worker started (daemon thread)");
            } else {
                log.info("Pipeline execution disabled (run-on-startup=false)");
            }
        };
    }

    /**
     * Start scheduled pipeline in background
     * This runs the scheduler which executes pipeline at regular intervals
     */
    private void startScheduledPipeline() {
        // Create a separate daemon thread for the scheduler
        Thread schedulerThread = new Thread(() -> {
            try {
                log.info("⏱️ Starting scheduler with {} minute interval", schedulerIntervalMinutes);
                dataScheduler.start(schedulerIntervalMinutes);
            } catch (Exception e) {
                log.error("❌ Scheduler failed: {}", e.getMessage(), e);
            }
        });
        schedulerThread.setDaemon(true);
        schedulerThread.setName("scheduler-worker");
        schedulerThread.start();
    }

    /**
     * Custom ThreadFactory for daemon threads
     * Matches Python's daemon=True behavior
     */
    static class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public DaemonThreadFactory() {
            this.namePrefix = "pipeline-worker-";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);  // Matches Python daemon=True
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}