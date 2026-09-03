package com.tunindex.market_tool.collector.services.scoring;

import com.tunindex.market_tool.collector.dto.scoring.OpportunityScoreDto;

import java.util.List;

public interface OpportunityService {

    /** Every tracked stock, scored and ranked best-first. */
    List<OpportunityScoreDto> findOpportunities(int limit, int minScore, boolean includeNews);

    /** One stock's full score breakdown. */
    OpportunityScoreDto scoreSymbol(String symbol);
}
