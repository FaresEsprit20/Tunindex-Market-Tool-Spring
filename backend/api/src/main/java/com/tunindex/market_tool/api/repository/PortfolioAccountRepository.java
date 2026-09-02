package com.tunindex.market_tool.api.repository;

import com.tunindex.market_tool.api.entities.PortfolioAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PortfolioAccountRepository extends JpaRepository<PortfolioAccount, Long> {
    Optional<PortfolioAccount> findByUserId(Integer userId);
}
