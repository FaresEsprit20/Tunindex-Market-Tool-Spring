package com.tunindex.market_tool.user_subscription.repository;

import com.tunindex.market_tool.user_subscription.entities.SubscriptionPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long>, JpaSpecificationExecutor<SubscriptionPlan> {

    Optional<SubscriptionPlan> findByName(String name);

    Optional<SubscriptionPlan> findByNameAndIsActiveTrue(String name);

    Page<SubscriptionPlan> findAllByIsActiveTrueOrderByDisplayOrderAsc(Pageable pageable);

    Page<SubscriptionPlan> findAllByIsActiveTrueOrderByPriceMonthlyAsc(Pageable pageable);

    @Query("SELECT p FROM SubscriptionPlan p WHERE p.priceMonthly <= :maxPrice AND p.isActive = true")
    Page<SubscriptionPlan> findActivePlansByMaxPrice(@Param("maxPrice") BigDecimal maxPrice, Pageable pageable);

    @Query("SELECT p FROM SubscriptionPlan p WHERE p.apiCallsLimit >= :minLimit")
    Page<SubscriptionPlan> findByApiCallsLimitGreaterThanEqual(@Param("minLimit") Integer minLimit, Pageable pageable);

    boolean existsByName(String name);

    long count(Specification<SubscriptionPlan> spec);
}