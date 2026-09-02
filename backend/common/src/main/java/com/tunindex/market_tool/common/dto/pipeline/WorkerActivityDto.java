package com.tunindex.market_tool.common.dto.pipeline;

public record WorkerActivityDto(
        String threadName,
        String symbol,
        PipelinePhase phase,
        long elapsedMs
) {
}
