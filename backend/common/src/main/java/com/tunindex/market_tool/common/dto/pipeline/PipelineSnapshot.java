package com.tunindex.market_tool.common.dto.pipeline;

import java.time.Instant;
import java.util.List;

public record PipelineSnapshot(
        PipelineState state,
        int totalStocks,
        int completedCount,
        int failedCount,
        int activeWorkerCount,
        int maxFetchWorkers,
        int maxSaveWorkers,
        Instant startedAt,
        Instant finishedAt,
        long elapsedMs,
        double throughputPerSec,
        List<WorkerActivityDto> activeWorkers,
        List<RecentEventDto> recentEvents
) {
}
