package com.tunindex.market_tool.collector.repository.jpa;

import com.tunindex.market_tool.collector.entities.MarketNews;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarketNewsRepository extends JpaRepository<MarketNews, Long> {

    List<MarketNews> findAllByOrderByPublishedAtDesc(Limit limit);

    boolean existsByUrl(String url);

    /** Freshness probe — drives the scrape-or-serve-cached decision. */
    Optional<MarketNews> findTopByOrderByScrapedAtDesc();
}
