package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.UserSubscription;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    List<UserSubscription> findAllByUserId(Long userId);

    List<UserSubscription> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserSubscription> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT us FROM UserSubscription us WHERE us.userId = :userId AND us.status = :status AND us.endDate > :now")
    Optional<UserSubscription> findActiveSubscriptionByUserId(@Param("userId") Long userId, @Param("status") SubscriptionStatus status, @Param("now") LocalDateTime now);

    @Query("SELECT us FROM UserSubscription us WHERE us.endDate < :now AND us.status = :status")
    List<UserSubscription> findExpiredSubscriptions(@Param("now") LocalDateTime now, @Param("status") SubscriptionStatus status);

    @Query("SELECT us FROM UserSubscription us WHERE us.endDate BETWEEN :start AND :end AND us.status = :status")
    List<UserSubscription> findSubscriptionsExpiringBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") SubscriptionStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE UserSubscription us SET us.status = :newStatus WHERE us.userId = :userId AND us.status = :currentStatus")
    int updateSubscriptionStatus(@Param("userId") Long userId, @Param("currentStatus") SubscriptionStatus currentStatus, @Param("newStatus") SubscriptionStatus newStatus);

    @Modifying
    @Transactional
    @Query("UPDATE UserSubscription us SET us.status = :status, us.cancelledAt = :cancelledAt, us.cancellationReason = :reason WHERE us.id = :id")
    int cancelSubscription(@Param("id") Long id, @Param("status") SubscriptionStatus status, @Param("cancelledAt") LocalDateTime cancelledAt, @Param("reason") String reason);

    long countByUserIdAndStatus(Long userId, SubscriptionStatus status);
}