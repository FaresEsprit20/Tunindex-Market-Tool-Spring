package com.tunindex.market_tool.collector.services.news;

import com.tunindex.market_tool.collector.dto.news.StockNewsDto;
import reactor.core.publisher.Mono;

import java.util.List;

public interface StockNewsService {

    /**
     * Serves stored news if it was scraped recently enough, otherwise
     * scrapes fresh, upserts, and serves the (now-current) stored list.
     */
    Mono<List<StockNewsDto>> getNews(String symbol, int limit);
}
