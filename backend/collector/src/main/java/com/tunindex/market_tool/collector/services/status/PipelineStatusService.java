package com.tunindex.market_tool.collector.services.status;

import com.tunindex.market_tool.common.dto.pipeline.PipelinePhase;
import com.tunindex.market_tool.common.dto.pipeline.PipelineSnapshot;
import com.tunindex.market_tool.common.dto.pipeline.PipelineState;
import com.tunindex.market_tool.common.dto.pipeline.RecentEventDto;
import com.tunindex.market_tool.common.dto.pipeline.WorkerActivityDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks the collector's scraping pipeline live: which worker threads are
 * doing what right now, how many stocks are done/failed, and a rolling feed
 * of recent completions. This is real JVM/Reactor state (actual thread names,
 * actual timings) captured as the pipeline runs — not a simulated animation —
 * pushed to subscribers (SSE) via a Reactor sink so the UI updates as it
 * genuinely happens.
 */
@Slf4j
@Service
public class PipelineStatusService {

    private record ActiveWorker(String threadName, String symbol, PipelinePhase phase, long startedAtMillis) {
    }

    private static final int MAX_RECENT_EVENTS = 40;

    private final AtomicReference<PipelineState> state = new AtomicReference<>(PipelineState.IDLE);
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicReference<Instant> startedAt = new AtomicReference<>();
    private final AtomicReference<Instant> finishedAt = new AtomicReference<>();

    // Keyed by threadName#phase so a thread doing fetch-then-save shows as two slots if concurrent.
    private final Map<String, ActiveWorker> activeWorkers = new ConcurrentHashMap<>();
    private final Deque<RecentEventDto> recentEvents = new ArrayDeque<>();
    private final Object recentEventsLock = new Object();

    private final Sinks.Many<PipelineSnapshot> sink = Sinks.many().replay().limit(1);

    public synchronized void start(int totalStocks) {
        state.set(PipelineState.RUNNING);
        total.set(totalStocks);
        completed.set(0);
        failed.set(0);
        startedAt.set(Instant.now());
        finishedAt.set(null);
        activeWorkers.clear();
        synchronized (recentEventsLock) {
            recentEvents.clear();
        }
        log.info("📡 Pipeline status: RUNNING ({} stocks queued)", totalStocks);
        emit();
    }

    public void workerStarted(String threadName, String symbol, PipelinePhase phase) {
        // Keyed by symbol, not thread: Netty's event-loop pool can be smaller
        // than the fetch concurrency, so two in-flight items can briefly
        // share a thread name. Each symbol only has one operation in flight
        // per phase, so it's the safe uniqueness key.
        activeWorkers.put(symbol + "#" + phase, new ActiveWorker(threadName, symbol, phase, System.currentTimeMillis()));
        emit();
    }

    public void workerFinished(String threadName, String symbol, PipelinePhase phase, boolean success) {
        ActiveWorker worker = activeWorkers.remove(symbol + "#" + phase);
        long duration = worker != null ? System.currentTimeMillis() - worker.startedAtMillis() : 0;

        if (phase == PipelinePhase.SAVING) {
            // Terminal for this symbol either way: SAVING only starts once
            // a fetch actually produced data, so success/failure here is final.
            if (success) {
                completed.incrementAndGet();
            } else {
                failed.incrementAndGet();
            }
        } else if (phase == PipelinePhase.FETCHING && !success) {
            // Also terminal: a symbol that comes back empty (no data found,
            // or the fetch errored) never reaches SAVING, so it must still
            // count here or completed+failed would never reach total.
            failed.incrementAndGet();
        }

        synchronized (recentEventsLock) {
            recentEvents.addFirst(new RecentEventDto(symbol, phase, success, duration, Instant.now()));
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.removeLast();
            }
        }
        emit();
    }

    public synchronized void finish(boolean success) {
        state.set(success ? PipelineState.COMPLETED : PipelineState.FAILED);
        finishedAt.set(Instant.now());
        activeWorkers.clear();
        log.info("📡 Pipeline status: {} ({} completed, {} failed)", state.get(), completed.get(), failed.get());
        emit();
    }

    public boolean isRunning() {
        return state.get() == PipelineState.RUNNING;
    }

    public Flux<PipelineSnapshot> stream() {
        return sink.asFlux().mergeWith(Flux.interval(Duration.ofSeconds(1)).map(tick -> currentSnapshot()));
    }

    public PipelineSnapshot currentSnapshot() {
        Instant started = startedAt.get();
        Instant finished = finishedAt.get();
        long elapsedMs = started == null ? 0 : (finished != null ? finished : Instant.now()).toEpochMilli() - started.toEpochMilli();
        int done = completed.get() + failed.get();
        double throughput = elapsedMs > 0 ? done / (elapsedMs / 1000.0) : 0;

        List<WorkerActivityDto> workers = activeWorkers.values().stream()
                .sorted(Comparator.comparing(ActiveWorker::startedAtMillis))
                .map(w -> new WorkerActivityDto(w.threadName(), w.symbol(), w.phase(), System.currentTimeMillis() - w.startedAtMillis()))
                .toList();

        List<RecentEventDto> events;
        synchronized (recentEventsLock) {
            events = List.copyOf(recentEvents);
        }

        int maxFetch = 5;
        int maxSave = 10;

        return new PipelineSnapshot(
                state.get(),
                total.get(),
                completed.get(),
                failed.get(),
                workers.size(),
                maxFetch,
                maxSave,
                started,
                finished,
                elapsedMs,
                throughput,
                workers,
                events
        );
    }

    private void emit() {
        sink.tryEmitNext(currentSnapshot());
    }
}
