package com.tunindex.market_tool.collector.internal.controllers;

import com.tunindex.market_tool.collector.dto.scoring.OpportunityScoreDto;
import com.tunindex.market_tool.collector.services.scoring.OpportunityService;
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

    private void validateApiKey(String apiKey) {
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            throw new InvalidEntityException(
                    "Invalid internal API key",
                    ErrorCodes.INVALID_PARAMETER,
                    Collections.singletonList("A valid X-API-Key header is required"));
        }
    }
}
