package com.tunindex.market_tool.collector.dto.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The BVMT trading session as of right now, derived from the configured
 * schedule in Africa/Tunis local time.
 *
 * <p>This is a clock, not an exchange feed: it reports what the published
 * timetable says should be happening, which is why {@code scheduleBased} is
 * on the payload and surfaced in the UI. It does not know about exchange
 * holidays or an unscheduled halt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSessionDto {

    /** PRE_OPEN | OPEN | PRE_CLOSE | CLOSED | WEEKEND */
    private String state;

    /** Short human label, e.g. "Continuous trading". */
    private String label;

    /** What happens next, e.g. "Opens" / "Closes". */
    private String nextTransitionLabel;

    /** Local Tunis time of the next transition. */
    private LocalDateTime nextTransitionAt;

    /** Seconds until that transition — lets the UI count down without a clock skew. */
    private long secondsUntilTransition;

    /** Server's current Tunis local time, so the client can show the exchange's clock. */
    private LocalDateTime tunisTime;

    private String timezone;

    /** Always true here — see the class comment. */
    private boolean scheduleBased;
}
