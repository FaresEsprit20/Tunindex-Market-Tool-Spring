package com.tunindex.market_tool.api.repository;

import com.tunindex.market_tool.api.entities.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByUserIdOrderByCreatedAtDesc(Integer userId);

    Optional<AlertRule> findByIdAndUserId(Long id, Integer userId);

    List<AlertRule> findByEnabledTrue();

    long countByUserIdAndEnabledTrue(Integer userId);
}
