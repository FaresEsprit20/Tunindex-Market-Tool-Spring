package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.Refund;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RefundRepositoryTest {

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long refund2Id;

    @BeforeEach
    void setUp() {
        // Clear all existing data first
        refundRepository.deleteAll();
        entityManager.flush();

        Refund refund1 = Refund.builder()
                .transactionId(100L)
                .amount(new BigDecimal("99.99"))
                .reason("Customer request")
                .status(RefundStatus.COMPLETED)
                .providerRefundId("refund_001")
                .refundDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Refund refund2 = Refund.builder()
                .transactionId(100L)
                .amount(new BigDecimal("50.00"))
                .reason("Partial refund")
                .status(RefundStatus.PENDING)
                .providerRefundId(null)
                .refundDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        Refund refund3 = Refund.builder()
                .transactionId(200L)
                .amount(new BigDecimal("199.99"))
                .reason("Service issue")
                .status(RefundStatus.FAILED)
                .providerRefundId(null)
                .failureReason("Insufficient balance")
                .refundDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        refund1 = entityManager.persistAndFlush(refund1);
        refund2 = entityManager.persistAndFlush(refund2);
        refund3 = entityManager.persistAndFlush(refund3);
        refund2Id = refund2.getId();
        entityManager.clear();
    }

    @Test
    void findAllByTransactionId_ShouldReturnRefundsForTransaction() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Refund> result = refundRepository.findAllByTransactionId(100L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting("amount")
                .containsExactlyInAnyOrder(new BigDecimal("99.99"), new BigDecimal("50.00"));
    }

    @Test
    void findAllByTransactionId_WhenNoRefunds_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Refund> result = refundRepository.findAllByTransactionId(999L, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void findByProviderRefundId_ShouldReturnRefund() {
        // When
        Optional<Refund> found = refundRepository.findByProviderRefundId("refund_001");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getProviderRefundId()).isEqualTo("refund_001");
        assertThat(found.get().getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    void findByProviderRefundId_WhenNotFound_ShouldReturnEmpty() {
        // When
        Optional<Refund> found = refundRepository.findByProviderRefundId("refund_not_exists");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void findAllByStatus_ShouldReturnRefundsWithStatus() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Refund> result = refundRepository.findAllByStatus(RefundStatus.PENDING, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(RefundStatus.PENDING);
    }

    @Test
    void findByTransactionIdOrderByCreatedAtDesc_ShouldReturnRefundsOrdered() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<Refund> result = refundRepository.findByTransactionIdOrderByCreatedAtDesc(100L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void updateRefundStatus_ShouldUpdateStatus() {
        // When
        int updatedCount = refundRepository.updateRefundStatus(refund2Id, RefundStatus.COMPLETED);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(updatedCount).isEqualTo(1);

        Optional<Refund> updated = refundRepository.findById(refund2Id);
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    void updateRefundWithProviderId_ShouldUpdateStatusAndProviderId() {
        // When
        int updatedCount = refundRepository.updateRefundWithProviderId(
                refund2Id, RefundStatus.COMPLETED, "refund_prov_002");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(updatedCount).isEqualTo(1);

        Optional<Refund> updated = refundRepository.findById(refund2Id);
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(updated.get().getProviderRefundId()).isEqualTo("refund_prov_002");
    }

    @Test
    void getTotalRefundedAmountForTransaction_ShouldReturnSum() {
        // When
        BigDecimal total = refundRepository.getTotalRefundedAmountForTransaction(100L, RefundStatus.COMPLETED);

        // Then
        assertThat(total).isEqualTo(new BigDecimal("99.99"));
    }

    @Test
    void getTotalRefundedAmountForTransaction_WhenNoCompletedRefunds_ShouldReturnZero() {
        // When
        BigDecimal total = refundRepository.getTotalRefundedAmountForTransaction(200L, RefundStatus.COMPLETED);

        // Then
        assertThat(total).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void existsByTransactionIdAndStatus_ShouldReturnTrueWhenExists() {
        // When
        boolean exists = refundRepository.existsByTransactionIdAndStatus(100L, RefundStatus.COMPLETED);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void existsByTransactionIdAndStatus_WhenNotExists_ShouldReturnFalse() {
        // When
        boolean exists = refundRepository.existsByTransactionIdAndStatus(100L, RefundStatus.PROCESSING);

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void findAllWithSpecification_ShouldFilterRefunds() {
        // Given
        Specification<Refund> spec = (root, query, cb) ->
                cb.equal(root.get("status"), RefundStatus.COMPLETED);

        // When
        var result = refundRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getProviderRefundId()).isEqualTo("refund_001");
    }

    @Test
    void countWithSpecification_ShouldReturnCount() {
        // Given
        Specification<Refund> spec = (root, query, cb) ->
                cb.equal(root.get("transactionId"), 100L);

        // When
        long count = refundRepository.count(spec);

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void save_ShouldPersistRefund() {
        // Given
        Refund newRefund = Refund.builder()
                .transactionId(300L)
                .amount(new BigDecimal("75.50"))
                .reason("Test refund")
                .status(RefundStatus.PENDING)
                .refundDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        // When
        Refund saved = refundRepository.save(newRefund);
        entityManager.flush();

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTransactionId()).isEqualTo(300L);
        assertThat(saved.getAmount()).isEqualTo(new BigDecimal("75.50"));
        assertThat(saved.getStatus()).isEqualTo(RefundStatus.PENDING);
    }
}