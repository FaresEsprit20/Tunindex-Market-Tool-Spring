package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.Refund;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
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
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long>, JpaSpecificationExecutor<Refund> {

    Page<Refund> findAllByTransactionId(Long transactionId, Pageable pageable);

    Optional<Refund> findByProviderRefundId(String providerRefundId);

    Page<Refund> findAllByStatus(RefundStatus status, Pageable pageable);

    @Query("SELECT r FROM Refund r WHERE r.transactionId = :transactionId ORDER BY r.createdAt DESC")
    Page<Refund> findByTransactionIdOrderByCreatedAtDesc(@Param("transactionId") Long transactionId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Refund r SET r.status = :newStatus WHERE r.id = :id")
    int updateRefundStatus(@Param("id") Long id, @Param("newStatus") RefundStatus newStatus);

    @Modifying
    @Transactional
    @Query("UPDATE Refund r SET r.status = :status, r.providerRefundId = :providerRefundId WHERE r.id = :id")
    int updateRefundWithProviderId(@Param("id") Long id, @Param("status") RefundStatus status, @Param("providerRefundId") String providerRefundId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.transactionId = :transactionId AND r.status = :status")
    BigDecimal getTotalRefundedAmountForTransaction(@Param("transactionId") Long transactionId, @Param("status") RefundStatus status);

    boolean existsByTransactionIdAndStatus(Long transactionId, RefundStatus status);

    long count(Specification<Refund> spec);
}