package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long>, JpaSpecificationExecutor<PaymentTransaction> {

    Optional<PaymentTransaction> findByTransactionId(String transactionId);

    Optional<PaymentTransaction> findByProviderPaymentId(String providerPaymentId);

    Page<PaymentTransaction> findAllByUserId(Long userId, Pageable pageable);

    Page<PaymentTransaction> findAllByStatus(PaymentStatus status, Pageable pageable);

    Page<PaymentTransaction> findAllByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime date, Pageable pageable);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.userId = :userId AND pt.status = :status")
    Page<PaymentTransaction> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") PaymentStatus status, Pageable pageable);

    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.subscriptionId = :subscriptionId")
    Page<PaymentTransaction> findBySubscriptionId(@Param("subscriptionId") Long subscriptionId, Pageable pageable);

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

    // Use Pageable for sorting instead of ORDER BY in query
    @Query("SELECT FUNCTION('DATE', pt.createdAt) as date, SUM(pt.amount) as total, COUNT(pt) as count " +
            "FROM PaymentTransaction pt WHERE pt.status = :status AND pt.createdAt >= :startDate " +
            "GROUP BY FUNCTION('DATE', pt.createdAt)")
    Page<Object[]> getDailyPaymentSummary(@Param("status") PaymentStatus status, @Param("startDate") LocalDateTime startDate, Pageable pageable);


    long count(Specification<PaymentTransaction> spec);
}