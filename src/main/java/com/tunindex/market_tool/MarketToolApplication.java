package com.tunindex.market_tool;

import com.tunindex.market_tool.domain.services.orchestrator.DataOrchestrator;
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
    private DataOrchestrator dataOrchestrator;

    @Value("${market-tool.scheduler.run-on-startup:true}")
    private boolean runOnStartup;

    @Value("${market-tool.scheduler.initial-delay-seconds:2}")
    private int initialDelaySeconds;

    @Value("${market-tool.parallelism.max-workers:10}")
    private int maxWorkers;

    @Value("${market-tool.scheduler.interval-minutes:30}")
    private int schedulerIntervalMinutes;

    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private static ExecutorService backgroundExecutor;
    private Thread schedulerThread;

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(MarketToolApplication.class, args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (isShuttingDown.compareAndSet(false, true)) {
                log.info("⏹️ Application shutting down...");
                if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
                    backgroundExecutor.shutdown();
                }
                context.close();
            }
        }, "shutdown-hook"));
    }

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
                backgroundExecutor = Executors.newFixedThreadPool(maxWorkers, new DaemonThreadFactory());

                backgroundExecutor.submit(() -> {
                    try {
                        Thread.sleep(initialDelaySeconds * 1000L);
                        log.info("🔄 Running initial pipeline on background thread...");
                        dataOrchestrator.runPipeline().block();
                        log.info("✅ Initial pipeline completed successfully");
                        startScheduler();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("❌ Pipeline execution failed: {}", e.getMessage(), e);
                    }
                });

                log.info("🚀 Background pipeline worker started (daemon thread)");
            }
        };
    }

    private void startScheduler() {
        schedulerThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(schedulerIntervalMinutes * 60 * 1000L);
                    log.info("🔄 Running scheduled pipeline...");
                    dataOrchestrator.runPipeline().block();
                }
            } catch (InterruptedException e) {
                log.info("Scheduler thread interrupted");
                Thread.currentThread().interrupt();
            }
        });
        schedulerThread.setDaemon(true);
        schedulerThread.setName("scheduler-worker");
        schedulerThread.start();
        log.info("⏱️ Scheduler started (every {} minutes)", schedulerIntervalMinutes);
    }

    static class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix = "pipeline-worker-";

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}