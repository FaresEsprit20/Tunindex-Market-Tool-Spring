package com.tunindex.market_tool.collector.services.news;

import com.tunindex.market_tool.collector.dto.news.MarketNewsDto;
import reactor.core.publisher.Mono;

import java.util.List;

public interface MarketNewsService {

    /**
     * Market-wide headlines, served from store when recently scraped and
     * refreshed from ilboursa otherwise. Each item carries the rule-based
     * sentiment tag and the related stock's day move where the source
     * provided one.
     */
    Mono<List<MarketNewsDto>> getMarketNews(int limit);
}
