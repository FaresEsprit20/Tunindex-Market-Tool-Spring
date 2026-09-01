package com.tunindex.market_tool.user_subscription.repository;

import com.tunindex.market_tool.user_subscription.entities.UserSubscription;
import com.tunindex.market_tool.user_subscription.entities.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long>, JpaSpecificationExecutor<UserSubscription> {

    // ========== BASIC QUERIES ==========

    Optional<UserSubscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    Page<UserSubscription> findAllByUserId(Long userId, Pageable pageable);

    Optional<UserSubscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT us FROM UserSubscription us WHERE us.userId = :userId AND us.status = :status AND us.endDate > :now")
    Optional<UserSubscription> findActiveSubscriptionByUserId(
            @Param("userId") Long userId,
            @Param("status") SubscriptionStatus status,
            @Param("now") LocalDateTime now);

    @Query("SELECT us FROM UserSubscription us WHERE us.endDate < :now AND us.status = :status")
    Page<UserSubscription> findExpiredSubscriptions(
            @Param("now") LocalDateTime now,
            @Param("status") SubscriptionStatus status,
            Pageable pageable);

    @Query("SELECT us FROM UserSubscription us WHERE us.endDate BETWEEN :start AND :end AND us.status = :status")
    Page<UserSubscription> findSubscriptionsExpiringBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") SubscriptionStatus status,
            Pageable pageable);

    // ========== AUTO-RENEWAL QUERIES ==========

    @Query("SELECT us FROM UserSubscription us WHERE us.autoRenew = true AND us.status = :status AND us.endDate BETWEEN :start AND :end")
    List<UserSubscription> findSubscriptionsToRenew(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("status") SubscriptionStatus status);

    @Query("SELECT us FROM UserSubscription us WHERE us.renewalFailed = true AND us.status = :status AND us.renewalAttempts < :maxAttempts")
    List<UserSubscription> findFailedRenewals(
            @Param("status") SubscriptionStatus status,
            @Param("maxAttempts") int maxAttempts);

    @Query("SELECT us FROM UserSubscription us WHERE us.endDate < :now AND us.autoRenew = true AND us.status = :status")
    List<UserSubscription> findExpiredAutoRenewSubscriptions(
            @Param("now") LocalDateTime now,
            @Param("status") SubscriptionStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE UserSubscription us SET us.renewalAttempts = us.renewalAttempts + 1, us.lastRenewalAttempt = :now, us.lastRenewalError = :error, us.renewalFailed = true WHERE us.id = :id")
    int incrementRenewalAttempt(
            @Param("id") Long id,
            @Param("now") LocalDateTime now,
            @Param("error") String error);

    @Modifying
    @Transactional
    @Query("UPDATE UserSubscription us SET us.renewalFailed = false, us.renewalAttempts = 0, us.lastRenewalError = null, us.status = :status WHERE us.id = :id")
    int resetRenewalStatus(
            @Param("id") Long id,
            @Param("status") SubscriptionStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE UserSubscription us SET us.endDate = :newEndDate, us.updatedAt = :now WHERE us.id = :id")
    int extendSubscriptionEndDate(
            @Param("id") Long id,
            @Param("newEndDate") LocalDateTime newEndDate,
            @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE UserSubscription us SET us.autoRenew = :autoRenew WHERE us.id = :id")
    int updateAutoRenewSetting(
            @Param("id") Long id,
            @Param("autoRenew") Boolean autoRenew);

    long countByUserIdAndStatus(Long userId, SubscriptionStatus status);
}