package com.tunindex.market_tool.collector.services.status;

import com.tunindex.market_tool.collector.services.orchestrator.DataOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single entry point for kicking off a pipeline run, used by both the
 * optional run-on-startup hook and the on-demand /pipeline/start endpoint.
 * Guards against overlapping runs and owns the recurring scheduler, which
 * only arms itself after the first successful manual/startup run — the
 * collector no longer scrapes anything until something (a person, or
 * config) actually asks it to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineRunnerService {

    private final DataOrchestrator dataOrchestrator;
    private final PipelineStatusService statusService;

    @Value("${market-tool.scheduler.interval-minutes:30}")
    private int schedulerIntervalMinutes;

    @Value("${market-tool.parallelism.fetch-workers:5}")
    private int fetchWorkers;

    private final AtomicBoolean schedulerStarted = new AtomicBoolean(false);

    public int totalKnownStocks() {
        return com.tunindex.market_tool.common.utils.constants.Constants.TUNISIAN_STOCKS_STOCK_ANALYSIS.size();
    }

    /**
     * @return true if a run was started, false if one was already in progress.
     */
    public synchronized boolean triggerRun() {
        if (statusService.isRunning()) {
            log.info("⏭️ Pipeline start requested but a run is already in progress");
            return false;
        }

        Mono.fromRunnable(this::runOnce)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        return true;
    }

    private void runOnce() {
        try {
            log.info("🔄 Running pipeline...");
            dataOrchestrator.runPipeline().block();
            log.info("✅ Pipeline run completed");
            ensureSchedulerStarted();
        } catch (Exception e) {
            log.error("❌ Pipeline run failed: {}", e.getMessage(), e);
        }
    }

    private void ensureSchedulerStarted() {
        if (schedulerStarted.compareAndSet(false, true)) {
            Thread schedulerThread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(schedulerIntervalMinutes * 60 * 1000L);
                        log.info("🔄 Running scheduled pipeline update...");
                        runOnce();
                    }
                } catch (InterruptedException e) {
                    log.info("Scheduler thread interrupted");
                    Thread.currentThread().interrupt();
                }
            }, "scheduler-worker");
            schedulerThread.setDaemon(true);
            schedulerThread.start();
            log.info("⏱️ Recurring scheduler armed (every {} minutes)", schedulerIntervalMinutes);
        }
    }
}
