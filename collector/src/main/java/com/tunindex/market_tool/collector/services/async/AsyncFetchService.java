package com.tunindex.market_tool.collector.services.async;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public interface AsyncFetchService {

    /**
     * Run a function over a list of items in parallel threads.
     * Returns a list of results in the same order as items.
     * Maps to Python: async_fetch.py - run_parallel(func, items, max_workers=20)
     */
    <T, R> List<R> runParallel(Function<T, R> func, List<T> items, int maxWorkers);

    /**
     * Run parallel with default max workers (20)
     */
    <T, R> List<R> runParallel(Function<T, R> func, List<T> items);

    /**
     * Run parallel and return results as they complete (reactive)
     */
    <T, R> Flux<R> runParallelReactive(Function<T, R> func, List<T> items, int maxWorkers);

    /**
     * Run parallel with index tracking
     */
    <T, R> ConcurrentHashMap<Integer, R> runParallelWithIndex(Function<T, R> func, List<T> items, int maxWorkers);
}