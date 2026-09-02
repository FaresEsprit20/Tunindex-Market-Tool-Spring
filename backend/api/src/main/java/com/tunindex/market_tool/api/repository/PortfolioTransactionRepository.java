package com.tunindex.market_tool.api.repository;

import com.tunindex.market_tool.api.entities.PortfolioTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortfolioTransactionRepository extends JpaRepository<PortfolioTransaction, Long> {
    List<PortfolioTransaction> findByAccountIdOrderByExecutedAtDesc(Long accountId);

    void deleteByAccountId(Long accountId);
}
