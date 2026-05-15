package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.Invoice;
import com.tunindex.market_tool.payment.entities.enums.InvoiceStatus;
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
public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByTransactionId(Long transactionId);

    Page<Invoice> findAllByUserId(Long userId, Pageable pageable);

    Page<Invoice> findAllByStatus(InvoiceStatus status, Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE i.userId = :userId AND i.status = :status")
    Page<Invoice> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") InvoiceStatus status, Pageable pageable);

    @Query("SELECT i FROM Invoice i WHERE i.dueDate < :now AND i.status = :status")
    Page<Invoice> findOverdueInvoices(@Param("now") LocalDateTime now, @Param("status") InvoiceStatus status, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE Invoice i SET i.status = :newStatus WHERE i.id = :id")
    int updateInvoiceStatus(@Param("id") Long id, @Param("newStatus") InvoiceStatus newStatus);

    @Modifying
    @Transactional
    @Query("UPDATE Invoice i SET i.status = :status, i.paidAt = :paidAt WHERE i.transactionId = :transactionId")
    int markInvoiceAsPaid(@Param("transactionId") Long transactionId, @Param("status") InvoiceStatus status, @Param("paidAt") LocalDateTime paidAt);

    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.userId = :userId AND i.status = :status")
    BigDecimal getTotalInvoicedAmountByUser(@Param("userId") Long userId, @Param("status") InvoiceStatus status);


    long count(Specification<Invoice> spec);
}