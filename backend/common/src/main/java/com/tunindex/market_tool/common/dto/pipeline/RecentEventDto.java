package com.tunindex.market_tool.common.dto.pipeline;

import java.time.Instant;

public record RecentEventDto(
        String symbol,
        PipelinePhase phase,
        boolean success,
        long durationMs,
        Instant timestamp
) {
}
