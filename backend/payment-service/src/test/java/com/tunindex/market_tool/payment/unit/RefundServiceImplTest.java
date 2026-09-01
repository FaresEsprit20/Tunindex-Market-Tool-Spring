package com.tunindex.market_tool.payment.unit;

import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.payment.dto.RefundPaymentRequestDto;
import com.tunindex.market_tool.payment.dto.RefundResponseDto;
import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.Refund;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import com.tunindex.market_tool.payment.entities.enums.RefundStatus;
import com.tunindex.market_tool.payment.repository.PaymentTransactionRepository;
import com.tunindex.market_tool.payment.repository.RefundRepository;
import com.tunindex.market_tool.payment.service.refund.RefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundServiceImpl Unit Tests")
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @InjectMocks
    private RefundServiceImpl refundService;

    private PaymentTransaction testTransaction;
    private Refund testRefund;
    private RefundPaymentRequestDto testRefundRequest;
    private PaginationAndFilteringDto paginationDto;

    @BeforeEach
    void setUp() {
        testTransaction = PaymentTransaction.builder()
                .id(1L)
                .userId(100L)
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.COMPLETED)
                .paymentDate(LocalDateTime.now().minusDays(1))
                .build();

        testRefund = Refund.builder()
                .id(1L)
                .transactionId(1L)
                .amount(new BigDecimal("100.00"))
                .reason("Customer request")
                .status(RefundStatus.PENDING)
                .providerRefundId("PROVIDER_REF_123")
                .failureReason(null)
                .refundDate(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        // Valid refund request with all required fields
        testRefundRequest = RefundPaymentRequestDto.builder()
                .transactionId("1")
                .providerPaymentId("PROVIDER_PAYMENT_123")
                .amount(new BigDecimal("100.00"))
                .reason("Customer request")
                .build();

        paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should return refund when valid id is provided")
        void shouldReturnRefundWhenValidIdProvided() {
            when(refundRepository.findById(1L)).thenReturn(Optional.of(testRefund));

            RefundResponseDto result = refundService.findById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTransactionId()).isEqualTo(1L);
            assertThat(result.getAmount()).isEqualByComparingTo("100.00");
            assertThat(result.getStatus()).isEqualTo(RefundStatus.PENDING);
            verify(refundRepository).findById(1L);
        }

        @Test
        @DisplayName("Should throw InvalidEntityException when id is null")
        void shouldThrowExceptionWhenIdIsNull() {
            assertThatThrownBy(() -> refundService.findById(null))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Invalid refund ID");
        }

        @Test
        @DisplayName("Should throw InvalidEntityException when id is zero or negative")
        void shouldThrowExceptionWhenIdIsInvalid() {
            assertThatThrownBy(() -> refundService.findById(0L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Invalid refund ID");

            assertThatThrownBy(() -> refundService.findById(-1L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Invalid refund ID");
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when refund not found")
        void shouldThrowExceptionWhenRefundNotFound() {
            when(refundRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.findById(999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Refund not found");
        }
    }

    @Nested
    @DisplayName("findByProviderRefundId Tests")
    class FindByProviderRefundIdTests {

        @Test
        @DisplayName("Should return refund when valid providerRefundId is provided")
        void shouldReturnRefundWhenValidProviderRefundIdProvided() {
            when(refundRepository.findByProviderRefundId("PROVIDER_REF_123"))
                    .thenReturn(Optional.of(testRefund));

            RefundResponseDto result = refundService.findByProviderRefundId("PROVIDER_REF_123");

            assertThat(result).isNotNull();
            assertThat(result.getProviderRefundId()).isEqualTo("PROVIDER_REF_123");
            verify(refundRepository).findByProviderRefundId("PROVIDER_REF_123");
        }

        @Test
        @DisplayName("Should throw InvalidEntityException when providerRefundId is null or empty")
        void shouldThrowExceptionWhenProviderRefundIdIsNullOrEmpty() {
            assertThatThrownBy(() -> refundService.findByProviderRefundId(null))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Invalid provider refund ID");

            assertThatThrownBy(() -> refundService.findByProviderRefundId(""))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Invalid provider refund ID");
        }

        @Test
        @DisplayName("Should throw EntityNotFoundException when refund not found")
        void shouldThrowExceptionWhenRefundNotFoundByProviderId() {
            when(refundRepository.findByProviderRefundId("NON_EXISTENT"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.findByProviderRefundId("NON_EXISTENT"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("requestRefund Tests")
    class RequestRefundTests {

        @Test
        @DisplayName("Should successfully request refund for valid transaction")
        void shouldSuccessfullyRequestRefund() {
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
            when(refundRepository.existsByTransactionIdAndStatus(1L, RefundStatus.COMPLETED))
                    .thenReturn(false);
            when(refundRepository.existsByTransactionIdAndStatus(1L, RefundStatus.PENDING))
                    .thenReturn(false);
            when(refundRepository.save(any(Refund.class))).thenReturn(testRefund);

            RefundResponseDto result = refundService.requestRefund(testRefundRequest, 100L);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(RefundStatus.PENDING);

            ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(refundCaptor.capture());

            Refund capturedRefund = refundCaptor.getValue();
            assertThat(capturedRefund.getTransactionId()).isEqualTo(1L);
            assertThat(capturedRefund.getAmount()).isEqualByComparingTo("100.00");
            assertThat(capturedRefund.getStatus()).isEqualTo(RefundStatus.PENDING);
        }

        @Test
        @DisplayName("Should throw exception when transaction not found")
        void shouldThrowExceptionWhenTransactionNotFound() {
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Payment transaction not found");
        }

        @Test
        @DisplayName("Should throw exception when user doesn't own the transaction")
        void shouldThrowExceptionWhenUserDoesNotOwnTransaction() {
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 999L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("You can only request refunds for your own transactions");
        }

        @Test
        @DisplayName("Should throw exception when transaction is not completed")
        void shouldThrowExceptionWhenTransactionNotCompleted() {
            testTransaction.setStatus(PaymentStatus.PENDING);
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Only completed transactions can be refunded");
        }

        @Test
        @DisplayName("Should throw exception when transaction is already refunded")
        void shouldThrowExceptionWhenTransactionAlreadyRefunded() {
            testTransaction.setStatus(PaymentStatus.REFUNDED);
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Transaction has already been refunded");
        }

        @Test
        @DisplayName("Should throw exception when refund amount doesn't match transaction amount")
        void shouldThrowExceptionWhenRefundAmountMismatch() {
            testRefundRequest.setAmount(new BigDecimal("50.00"));
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Refund must be for the full amount");
        }

        @Test
        @DisplayName("Should throw exception when completed refund already exists")
        void shouldThrowExceptionWhenCompletedRefundExists() {
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
            when(refundRepository.existsByTransactionIdAndStatus(1L, RefundStatus.COMPLETED))
                    .thenReturn(true);

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("A refund has already been processed");
        }

        @Test
        @DisplayName("Should throw exception when pending refund already exists")
        void shouldThrowExceptionWhenPendingRefundExists() {
            when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
            when(refundRepository.existsByTransactionIdAndStatus(1L, RefundStatus.COMPLETED))
                    .thenReturn(false);
            when(refundRepository.existsByTransactionIdAndStatus(1L, RefundStatus.PENDING))
                    .thenReturn(true);

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("A refund request is already pending");
        }

        @Test
        @DisplayName("Should throw exception when transaction ID format is invalid")
        void shouldThrowExceptionWhenTransactionIdFormatInvalid() {
            testRefundRequest.setTransactionId("invalid");

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessageContaining("Invalid transaction ID format");
        }

        @Test
        @DisplayName("Should throw InvalidEntityException when providerPaymentId is missing")
        void shouldThrowExceptionWhenProviderPaymentIdMissing() {
            testRefundRequest.setProviderPaymentId(null);

            assertThatThrownBy(() -> refundService.requestRefund(testRefundRequest, 100L))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessage("Invalid refund request");
        }
    }

    @Nested
    @DisplayName("updateRefundStatus Tests")
    class UpdateRefundStatusTests {

        @Test
        @DisplayName("Should successfully update refund status to COMPLETED")
        void shouldSuccessfullyUpdateToCompleted() {
            when(refundRepository.findById(1L)).thenReturn(Optional.of(testRefund));
            when(refundRepository.save(any(Refund.class))).thenReturn(testRefund);
            // Only stub if needed - this might not be called in this test path
            lenient().when(refundRepository.getTotalRefundedAmountForTransaction(eq(1L), eq(RefundStatus.COMPLETED)))
                    .thenReturn(new BigDecimal("100.00"));

            RefundResponseDto result = refundService.updateRefundStatus(1L, RefundStatus.COMPLETED);

            assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            verify(refundRepository).save(testRefund);
        }

        @Test
        @DisplayName("Should throw exception when updating completed refund")
        void shouldThrowExceptionWhenUpdatingCompletedRefund() {
            Refund completedRefund = Refund.builder()
                    .id(1L)
                    .transactionId(1L)
                    .amount(new BigDecimal("100.00"))
                    .status(RefundStatus.COMPLETED)
                    .build();
            when(refundRepository.findById(1L)).thenReturn(Optional.of(completedRefund));

            assertThatThrownBy(() -> refundService.updateRefundStatus(1L, RefundStatus.FAILED))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessage("Invalid refund status transition");
        }

        @Test
        @DisplayName("Should throw exception when changing failed refund to completed")
        void shouldThrowExceptionWhenChangingFailedToCompleted() {
            Refund failedRefund = Refund.builder()
                    .id(1L)
                    .transactionId(1L)
                    .amount(new BigDecimal("100.00"))
                    .status(RefundStatus.FAILED)
                    .build();
            when(refundRepository.findById(1L)).thenReturn(Optional.of(failedRefund));

            assertThatThrownBy(() -> refundService.updateRefundStatus(1L, RefundStatus.COMPLETED))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessage("Invalid refund status transition");
        }

        @Test
        @DisplayName("Should throw exception when refund not found")
        void shouldThrowExceptionWhenRefundNotFoundForUpdate() {
            when(refundRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.updateRefundStatus(999L, RefundStatus.COMPLETED))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("markAsCompleted Tests")
    class MarkAsCompletedTests {

        @Test
        @DisplayName("Should successfully mark refund as completed")
        void shouldSuccessfullyMarkAsCompleted() {
            when(refundRepository.findById(1L)).thenReturn(Optional.of(testRefund));
            when(refundRepository.save(any(Refund.class))).thenReturn(testRefund);

            RefundResponseDto result = refundService.markAsCompleted(1L);

            assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            verify(refundRepository).save(testRefund);
        }
    }

    @Nested
    @DisplayName("markAsFailed Tests")
    class MarkAsFailedTests {

        @Test
        @DisplayName("Should successfully mark refund as failed with reason")
        void shouldSuccessfullyMarkAsFailedWithReason() {
            when(refundRepository.findById(1L)).thenReturn(Optional.of(testRefund));
            when(refundRepository.save(any(Refund.class))).thenReturn(testRefund);

            RefundResponseDto result = refundService.markAsFailed(1L, "Payment gateway error");

            assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);

            ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(refundCaptor.capture());

            Refund capturedRefund = refundCaptor.getValue();
            assertThat(capturedRefund.getFailureReason()).isEqualTo("Payment gateway error");
        }

        @Test
        @DisplayName("Should throw exception when refund not found")
        void shouldThrowExceptionWhenRefundNotFoundForFail() {
            when(refundRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refundService.markAsFailed(999L, "Error"))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findAllByTransactionId Tests")
    class FindAllByTransactionIdTests {

        @Test
        @DisplayName("Should return paged refunds for transaction")
        void shouldReturnPagedRefundsForTransaction() {
            Page<Refund> refundPage = new PageImpl<>(List.of(testRefund));
            when(refundRepository.findAllByTransactionId(eq(1L), any(Pageable.class)))
                    .thenReturn(refundPage);

            var result = refundService.findAllByTransactionId(1L, paginationDto);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should throw exception when pagination parameters are invalid")
        void shouldThrowExceptionWhenPaginationInvalid() {
            paginationDto.setPage(0);

            assertThatThrownBy(() -> refundService.findAllByTransactionId(1L, paginationDto))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessage("Invalid pagination parameters");

            paginationDto.setPage(1);
            paginationDto.setSize(0);

            assertThatThrownBy(() -> refundService.findAllByTransactionId(1L, paginationDto))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessage("Invalid pagination parameters");

            paginationDto.setSize(101);

            assertThatThrownBy(() -> refundService.findAllByTransactionId(1L, paginationDto))
                    .isInstanceOf(InvalidEntityException.class)
                    .hasMessage("Invalid pagination parameters");
        }
    }

    @Nested
    @DisplayName("findAllByStatus Tests")
    class FindAllByStatusTests {

        @Test
        @DisplayName("Should return paged refunds by status")
        void shouldReturnPagedRefundsByStatus() {
            Page<Refund> refundPage = new PageImpl<>(List.of(testRefund));
            when(refundRepository.findAllByStatus(eq(RefundStatus.PENDING), any(Pageable.class)))
                    .thenReturn(refundPage);

            var result = refundService.findAllByStatus(RefundStatus.PENDING, paginationDto);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(RefundStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("filterRefunds Tests")
    class FilterRefundsTests {

        @Test
        @DisplayName("Should filter refunds with specifications")
        void shouldFilterRefundsWithSpecifications() {
            Map<String, String> filters = new HashMap<>();
            filters.put("status", "PENDING");
            filters.put("reason", "customer");
            paginationDto.setFilters(filters);

            Page<Refund> refundPage = new PageImpl<>(List.of(testRefund));
            when(refundRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(refundPage);

            var result = refundService.filterRefunds(paginationDto);

            assertThat(result.getContent()).hasSize(1);
            verify(refundRepository).findAll(any(Specification.class), any(Pageable.class));
        }

        @Test
        @DisplayName("Should handle empty filters")
        void shouldHandleEmptyFilters() {
            Page<Refund> refundPage = new PageImpl<>(List.of(testRefund));
            when(refundRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(refundPage);

            var result = refundService.filterRefunds(paginationDto);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should handle invalid filter values gracefully")
        void shouldHandleInvalidFilterValues() {
            Map<String, String> filters = new HashMap<>();
            filters.put("transactionId", "invalid");
            filters.put("status", "INVALID_STATUS");
            filters.put("minAmount", "not-a-number");
            paginationDto.setFilters(filters);

            Page<Refund> refundPage = new PageImpl<>(List.of(testRefund));
            when(refundRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(refundPage);

            var result = refundService.filterRefunds(paginationDto);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("updateRefundWithProviderId Tests")
    class UpdateRefundWithProviderIdTests {

        @Test
        @DisplayName("Should update refund with provider ID")
        void shouldUpdateRefundWithProviderId() {
            when(refundRepository.findById(1L)).thenReturn(Optional.of(testRefund));
            when(refundRepository.save(any(Refund.class))).thenReturn(testRefund);

            RefundResponseDto result = refundService.updateRefundWithProviderId(
                    1L, RefundStatus.COMPLETED, "NEW_PROVIDER_ID");

            assertThat(result.getProviderRefundId()).isEqualTo("NEW_PROVIDER_ID");

            ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
            verify(refundRepository).save(refundCaptor.capture());

            assertThat(refundCaptor.getValue().getProviderRefundId()).isEqualTo("NEW_PROVIDER_ID");
        }
    }

    @Nested
    @DisplayName("getTotalRefundedAmountForTransaction Tests")
    class GetTotalRefundedAmountTests {

        @Test
        @DisplayName("Should return total refunded amount")
        void shouldReturnTotalRefundedAmount() {
            when(refundRepository.getTotalRefundedAmountForTransaction(1L, RefundStatus.COMPLETED))
                    .thenReturn(new BigDecimal("100.00"));

            BigDecimal result = refundService.getTotalRefundedAmountForTransaction(1L);

            assertThat(result).isEqualByComparingTo("100.00");
            verify(refundRepository).getTotalRefundedAmountForTransaction(1L, RefundStatus.COMPLETED);
        }

        @Test
        @DisplayName("Should return zero when no refunds exist")
        void shouldReturnZeroWhenNoRefunds() {
            when(refundRepository.getTotalRefundedAmountForTransaction(1L, RefundStatus.COMPLETED))
                    .thenReturn(BigDecimal.ZERO);

            BigDecimal result = refundService.getTotalRefundedAmountForTransaction(1L);

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("hasExistingRefund Tests")
    class HasExistingRefundTests {

        @Test
        @DisplayName("Should return true when completed refund exists")
        void shouldReturnTrueWhenCompletedRefundExists() {
            when(refundRepository.existsByTransactionIdAndStatus(1L, RefundStatus.COMPLETED))
                    .thenReturn(true);

            boolean result = refundService.hasExistingRefund(1L);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when no completed refund exists")
        void shouldReturnFalseWhenNoCompletedRefundExists() {
            when(refundRepository.existsByTransactionIdAndStatus(1L, RefundStatus.COMPLETED))
                    .thenReturn(false);

            boolean result = refundService.hasExistingRefund(1L);

            assertThat(result).isFalse();
        }

    }
}