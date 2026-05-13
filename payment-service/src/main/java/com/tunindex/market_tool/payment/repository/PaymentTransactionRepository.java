package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    Optional<PaymentTransaction> findByProviderPaymentId(String providerPaymentId);

    List<PaymentTransaction> findAllByUserId(Long userId);

    Page<PaymentTransaction> findAllByUserId(Long userId, Pageable pageable);

    List<PaymentTransaction> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<PaymentTransaction> findAllByStatus(PaymentStatus status);

    List<PaymentTransaction> findAllByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime date);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.status = :status")
    List<PaymentTransaction> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") PaymentStatus status);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.subscriptionId = :subscriptionId ORDER BY pt.createdAt DESC")
    List<PaymentTransaction> findBySubscriptionId(@Param("subscriptionId") Long subscriptionId);

    @Modifying
    @Transactional
    @Query("UPDATE PaymentTransaction pt SET pt.status = :newStatus WHERE pt.transactionId = :transactionId")
    int updateTransactionStatus(@Param("transactionId") String transactionId, @Param("newStatus") PaymentStatus newStatus);

    @Modifying
    @Transactional
    @Query("UPDATE PaymentTransaction pt SET pt.status = :newStatus, pt.failureReason = :reason WHERE pt.transactionId = :transactionId")
    int updateTransactionStatusWithReason(@Param("transactionId") String transactionId, @Param("newStatus") PaymentStatus newStatus, @Param("reason") String reason);

    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.status = :status")
    BigDecimal getTotalAmountSpentByUser(@Param("userId") Long userId, @Param("status") PaymentStatus status);

    @Query("SELECT COUNT(pt) FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.status = :status")
    long countSuccessfulPaymentsByUser(@Param("userId") Long userId, @Param("status") PaymentStatus status);

    @Query("SELECT FUNCTION('DATE', pt.createdAt) as date, SUM(pt.amount) as total, COUNT(pt) as count " +
            "FROM PaymentTransaction pt WHERE pt.status = :status AND pt.createdAt >= :startDate " +
            "GROUP BY FUNCTION('DATE', pt.createdAt) ORDER BY date DESC")
    List<Object[]> getDailyPaymentSummary(@Param("status") PaymentStatus status, @Param("startDate") LocalDateTime startDate);
}