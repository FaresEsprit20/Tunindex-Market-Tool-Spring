package com.tunindex.market_tool.domain.services.impl;

import com.tunindex.market_tool.domain.services.async.AsyncFetchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

@Service
@Slf4j
public class AsyncFetchServiceImpl implements AsyncFetchService {

    private static final int DEFAULT_MAX_WORKERS = 20;

    @Override
    public <T, R> List<R> runParallel(Function<T, R> func, List<T> items, int maxWorkers) {
        log.debug("Running parallel execution for {} items with {} workers", items.size(), maxWorkers);

        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }

        int size = items.size();
        @SuppressWarnings("unchecked")
        R[] results = (R[]) new Object[size];

        ExecutorService executor = Executors.newFixedThreadPool(maxWorkers);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            final int index = i;
            final T item = items.get(i);

            Future<Void> future = executor.submit(() -> {
                try {
                    results[index] = func.apply(item);
                } catch (Exception e) {
                    log.error("Error processing item at index {}: {}", index, e.getMessage());
                    results[index] = null;
                }
                return null;
            });
            futures.add(future);
        }

        // Wait for all tasks to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error("Task failed: {}", e.getMessage());
            }
        }

        executor.shutdown();

        return List.of(results);
    }

    @Override
    public <T, R> List<R> runParallel(Function<T, R> func, List<T> items) {
        return runParallel(func, items, DEFAULT_MAX_WORKERS);
    }

    @Override
    public <T, R> Flux<R> runParallelReactive(Function<T, R> func, List<T> items, int maxWorkers) {
        log.debug("Running reactive parallel execution for {} items with {} workers", items.size(), maxWorkers);

        if (items == null || items.isEmpty()) {
            return Flux.empty();
        }

        return Flux.fromIterable(items)
                .parallel(maxWorkers)
                .runOn(Schedulers.boundedElastic())
                .map(item -> {
                    try {
                        return func.apply(item);
                    } catch (Exception e) {
                        log.error("Failed to process item: {}", e.getMessage());
                        return null;
                    }
                })
                .sequential()
                .filter(result -> result != null);
    }

    @Override
    public <T, R> ConcurrentHashMap<Integer, R> runParallelWithIndex(Function<T, R> func, List<T> items, int maxWorkers) {
        log.debug("Running parallel execution with index for {} items with {} workers", items.size(), maxWorkers);

        ConcurrentHashMap<Integer, R> resultMap = new ConcurrentHashMap<>();

        if (items == null || items.isEmpty()) {
            return resultMap;
        }

        ExecutorService executor = Executors.newFixedThreadPool(maxWorkers);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final T item = items.get(i);

            Future<Void> future = executor.submit(() -> {
                try {
                    R result = func.apply(item);
                    resultMap.put(index, result);
                } catch (Exception e) {
                    log.error("Error processing item at index {}: {}", index, e.getMessage());
                    resultMap.put(index, null);
                }
                return null;
            });
            futures.add(future);
        }

        // Wait for all tasks to complete
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                log.error("Task failed: {}", e.getMessage());
            }
        }

        executor.shutdown();

        return resultMap;
    }
}