package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByName(String name);

    Optional<SubscriptionPlan> findByNameAndIsActiveTrue(String name);

    List<SubscriptionPlan> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    List<SubscriptionPlan> findAllByIsActiveTrueOrderByPriceMonthlyAsc();

    @Query("SELECT p FROM SubscriptionPlan p WHERE p.priceMonthly <= :maxPrice AND p.isActive = true")
    List<SubscriptionPlan> findActivePlansByMaxPrice(@Param("maxPrice") BigDecimal maxPrice);

    boolean existsByName(String name);
}