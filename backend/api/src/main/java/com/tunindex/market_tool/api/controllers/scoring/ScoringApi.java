package com.tunindex.market_tool.api.controllers.scoring;

import com.tunindex.market_tool.api.dto.scoring.OpportunityScoreResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static com.tunindex.market_tool.common.utils.constants.Constants.APP_ROOT;

@Tag(name = "Tunindex Scorer", description = "Rule-based buy-opportunity scoring across every tracked stock")
public interface ScoringApi {

    @GetMapping(value = APP_ROOT + "/opportunities", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Ranked buy opportunities, best Tunindex Score first")
    List<OpportunityScoreResponseDto> opportunities(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "minScore", defaultValue = "0") int minScore,
            @RequestParam(value = "includeNews", defaultValue = "true") boolean includeNews);

    @GetMapping(value = APP_ROOT + "/score/{symbol}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "One stock's Tunindex Score with its full component breakdown")
    OpportunityScoreResponseDto score(@PathVariable("symbol") String symbol);
}
