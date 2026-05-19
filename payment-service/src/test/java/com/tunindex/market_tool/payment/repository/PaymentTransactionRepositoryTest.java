package com.tunindex.market_tool.payment.repository;

import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentTransactionRepositoryTest {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private PaymentTransaction transaction1;
    private PaymentTransaction transaction2;
    private PaymentTransaction transaction3;

    @BeforeEach
    void setUp() {
        // Create test data
        transaction1 = PaymentTransaction.builder()
                .transactionId("TXN-001")
                .userId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("TND")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.COMPLETED)
                .providerPaymentId("prov_001")
                .providerName("KONNECT")
                .description("Subscription payment")
                .subscriptionId(100L)
                .paymentDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        transaction2 = PaymentTransaction.builder()
                .transactionId("TXN-002")
                .userId(1L)
                .amount(new BigDecimal("49.99"))
                .currency("TND")
                .paymentMethod(PaymentMethod.E_DINAR)
                .status(PaymentStatus.PENDING)
                .providerPaymentId("prov_002")
                .providerName("KONNECT")
                .description("One-time purchase")
                .subscriptionId(null)
                .paymentDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        transaction3 = PaymentTransaction.builder()
                .transactionId("TXN-003")
                .userId(2L)
                .amount(new BigDecimal("199.99"))
                .currency("USD")
                .paymentMethod(PaymentMethod.FLOUCI)
                .status(PaymentStatus.FAILED)
                .providerPaymentId("prov_003")
                .providerName("KONNECT")
                .description("Yearly subscription")
                .subscriptionId(200L)
                .failureReason("Insufficient funds")
                .paymentDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        entityManager.persist(transaction1);
        entityManager.persist(transaction2);
        entityManager.persist(transaction3);
        entityManager.flush();
    }

    @Test
    void findByTransactionId_ShouldReturnTransaction() {
        // When
        Optional<PaymentTransaction> found = paymentTransactionRepository.findByTransactionId("TXN-001");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTransactionId()).isEqualTo("TXN-001");
        assertThat(found.get().getAmount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void findByTransactionId_WhenNotFound_ShouldReturnEmpty() {
        // When
        Optional<PaymentTransaction> found = paymentTransactionRepository.findByTransactionId("TXN-NOT-EXISTS");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void findByProviderPaymentId_ShouldReturnTransaction() {
        // When
        Optional<PaymentTransaction> found = paymentTransactionRepository.findByProviderPaymentId("prov_002");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getProviderPaymentId()).isEqualTo("prov_002");
        assertThat(found.get().getTransactionId()).isEqualTo("TXN-002");
    }

    @Test
    void findAllByUserId_ShouldReturnTransactionsForUser() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<PaymentTransaction> result = paymentTransactionRepository.findAllByUserId(1L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting("transactionId")
                .containsExactlyInAnyOrder("TXN-001", "TXN-002");
    }

    @Test
    void findAllByUserId_WhenNoTransactions_ShouldReturnEmptyPage() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<PaymentTransaction> result = paymentTransactionRepository.findAllByUserId(999L, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void findAllByStatus_ShouldReturnTransactionsWithStatus() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<PaymentTransaction> result = paymentTransactionRepository.findAllByStatus(PaymentStatus.COMPLETED, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionId()).isEqualTo("TXN-001");
    }

    @Test
    void findAllByStatusAndCreatedAtBefore_ShouldReturnOldTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime futureDate = LocalDateTime.now().plusDays(1);

        // When
        Page<PaymentTransaction> result = paymentTransactionRepository.findAllByStatusAndCreatedAtBefore(
                PaymentStatus.PENDING, futureDate, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionId()).isEqualTo("TXN-002");
    }

    @Test
    void findByUserIdAndStatus_ShouldReturnFilteredTransactions() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<PaymentTransaction> result = paymentTransactionRepository.findByUserIdAndStatus(1L, PaymentStatus.COMPLETED, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTransactionId()).isEqualTo("TXN-001");
    }

    @Test
    void findBySubscriptionId_ShouldReturnTransactionsForSubscription() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // When
        Page<PaymentTransaction> result = paymentTransactionRepository.findBySubscriptionId(100L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSubscriptionId()).isEqualTo(100L);
    }

    @Test
    @Transactional
    void updateTransactionStatus_ShouldUpdateStatus() {
        // When
        int updatedCount = paymentTransactionRepository.updateTransactionStatus("TXN-002", PaymentStatus.COMPLETED);
        entityManager.flush();

        // Then
        assertThat(updatedCount).isEqualTo(1);

        Optional<PaymentTransaction> updated = paymentTransactionRepository.findByTransactionId("TXN-002");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @Transactional
    void updateTransactionStatusWithReason_ShouldUpdateStatusAndReason() {
        // When
        int updatedCount = paymentTransactionRepository.updateTransactionStatusWithReason(
                "TXN-003", PaymentStatus.REFUNDED, "Customer requested refund");
        entityManager.flush();

        // Then
        assertThat(updatedCount).isEqualTo(1);

        Optional<PaymentTransaction> updated = paymentTransactionRepository.findByTransactionId("TXN-003");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(updated.get().getFailureReason()).isEqualTo("Customer requested refund");
    }

    @Test
    void getTotalAmountSpentByUser_ShouldReturnSum() {
        // When
        BigDecimal total = paymentTransactionRepository.getTotalAmountSpentByUser(1L, PaymentStatus.COMPLETED);

        // Then
        assertThat(total).isEqualTo(new BigDecimal("99.99"));
    }

    @Test
    void getTotalAmountSpentByUser_WhenNoTransactions_ShouldReturnZero() {
        // When
        BigDecimal total = paymentTransactionRepository.getTotalAmountSpentByUser(999L, PaymentStatus.COMPLETED);

        // Then
        assertThat(total).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void countSuccessfulPaymentsByUser_ShouldReturnCount() {
        // When
        long count = paymentTransactionRepository.countSuccessfulPaymentsByUser(1L, PaymentStatus.COMPLETED);

        // Then
        assertThat(count).isEqualTo(1);
    }

    @Test
    void getDailyPaymentSummary_ShouldReturnSummary() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        LocalDateTime startDate = LocalDateTime.now().minusDays(7);

        // When
        Page<Object[]> summary = paymentTransactionRepository.getDailyPaymentSummary(PaymentStatus.COMPLETED, startDate, pageable);

        // Then
        assertThat(summary.getContent()).isNotEmpty();
    }

    @Test
    void findAllWithSpecification_ShouldFilterTransactions() {
        // Given
        Specification<PaymentTransaction> spec = (root, query, cb) ->
                cb.equal(root.get("status"), PaymentStatus.COMPLETED);

        // When
        List<PaymentTransaction> result = paymentTransactionRepository.findAll(spec);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransactionId()).isEqualTo("TXN-001");
    }

    @Test
    void countWithSpecification_ShouldReturnCount() {
        // Given
        Specification<PaymentTransaction> spec = (root, query, cb) ->
                cb.equal(root.get("userId"), 1L);

        // When
        long count = paymentTransactionRepository.count(spec);

        // Then
        assertThat(count).isEqualTo(2);
    }
}