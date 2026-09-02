package com.tunindex.market_tool.api.repository;

import com.tunindex.market_tool.api.entities.PortfolioPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PortfolioPositionRepository extends JpaRepository<PortfolioPosition, Long> {
    List<PortfolioPosition> findByAccountIdOrderBySymbolAsc(Long accountId);

    Optional<PortfolioPosition> findByAccountIdAndSymbol(Long accountId, String symbol);
}
