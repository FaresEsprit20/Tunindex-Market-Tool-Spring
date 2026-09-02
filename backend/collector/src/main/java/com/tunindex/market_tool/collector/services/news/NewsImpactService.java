package com.tunindex.market_tool.collector.services.news;

import com.tunindex.market_tool.collector.dto.news.NewsImpactDto;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NewsImpactService {

    /** Ensures fresh news is scraped first, then pairs each headline with real price movement. */
    Mono<List<NewsImpactDto>> getImpact(String symbol, int limit, int tradingDaysAfter);
}
