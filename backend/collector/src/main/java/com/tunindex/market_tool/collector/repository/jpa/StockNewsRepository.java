package com.tunindex.market_tool.collector.repository.jpa;

import com.tunindex.market_tool.collector.entities.StockNews;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockNewsRepository extends JpaRepository<StockNews, Long> {

    List<StockNews> findBySymbolOrderByPublishedAtDesc(String symbol, Limit limit);

    boolean existsBySymbolAndUrl(String symbol, String url);

    Optional<StockNews> findTopBySymbolOrderByScrapedAtDesc(String symbol);
}
