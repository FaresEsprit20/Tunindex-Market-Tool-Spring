package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.scoring.OpportunityScoreDto;
import com.tunindex.market_tool.collector.services.scoring.OpportunityService;
import com.tunindex.market_tool.collector.services.scoring.ScoreSnapshotService;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/internal/scoring")
@RequiredArgsConstructor
@Slf4j
public class ScoringController {

    private final OpportunityService opportunityService;
    private final ScoreSnapshotService scoreSnapshotService;

    @Value("${internal.api.key}")
    private String internalApiKey;

    /** Ranked buy candidates across every tracked stock. */
    @GetMapping("/opportunities")
    public List<OpportunityScoreDto> opportunities(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int minScore,
            @RequestParam(defaultValue = "true") boolean includeNews,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        return opportunityService.findOpportunities(Math.min(Math.max(limit, 1), 100), minScore, includeNews);
    }

    /** One stock's score breakdown. */
    @GetMapping("/score/{symbol}")
    public OpportunityScoreDto score(
            @PathVariable String symbol,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        return opportunityService.scoreSymbol(symbol);
    }

    /** One symbol's score over time — what the history sparkline draws. */
    @GetMapping("/history/{symbol}")
    public java.util.List<java.util.Map<String, Object>> history(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "90") int days,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        return scoreSnapshotService.history(symbol, Math.min(Math.max(days, 7), 365)).stream()
                .map(row -> {
                    java.util.Map<String, Object> point = new java.util.LinkedHashMap<>();
                    point.put("date", row.getSnapshotDate().toString());
                    point.put("overallScore", row.getOverallScore());
                    point.put("verdict", row.getVerdict());
                    point.put("closePrice", row.getClosePrice());
                    return point;
                })
                .toList();
    }

    /**
     * Captures today's snapshot on demand, alongside the daily schedule.
     *
     * <p>Deliberately takes no date. Scoring reads current fundamentals, so
     * writing that result under a past date would fabricate a history that
     * never happened — and a score series is only worth having if every
     * point is what the scorer actually said on that day. Genuine history
     * accumulates forward, one day at a time.
     */
    @PostMapping("/snapshot")
    public java.util.Map<String, Object> snapshot(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey) {

        validateApiKey(apiKey);
        java.time.LocalDate today = java.time.LocalDate.now();
        int written = scoreSnapshotService.capture(today);
        return java.util.Map.of("date", today.toString(), "symbolsRecorded", written);
    }

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            throw new InvalidEntityException(
                    "Invalid internal API key",
                    ErrorCodes.INVALID_PARAMETER,
                    Collections.singletonList("A valid X-API-Key header is required"));
        }
    }
}
