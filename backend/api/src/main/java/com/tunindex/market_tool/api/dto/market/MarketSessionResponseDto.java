package com.tunindex.market_tool.api.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Passthrough of the collector's MarketSessionDto. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSessionResponseDto {
    private String state;
    private String label;
    private String nextTransitionLabel;
    private LocalDateTime nextTransitionAt;
    private long secondsUntilTransition;
    private LocalDateTime tunisTime;
    private String timezone;
    private boolean scheduleBased;
}
