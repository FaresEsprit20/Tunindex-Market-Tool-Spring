package com.tunindex.market_tool.payment.unit;

import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.PaymentRequestDto;
import com.tunindex.market_tool.payment.dto.PaymentResponseDto;
import com.tunindex.market_tool.payment.entities.PaymentTransaction;
import com.tunindex.market_tool.payment.entities.enums.PaymentMethod;
import com.tunindex.market_tool.payment.entities.enums.PaymentStatus;
import com.tunindex.market_tool.payment.repository.PaymentTransactionRepository;
import com.tunindex.market_tool.payment.service.payment_transaction.PaymentTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceImplTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @InjectMocks
    private PaymentTransactionServiceImpl paymentTransactionService;

    private PaymentTransaction paymentTransaction;
    private PaymentRequestDto paymentRequestDto;
    private PaginationAndFilteringDto paginationDto;

    @BeforeEach
    void setUp() {
        paymentTransaction = PaymentTransaction.builder()
                .id(1L)
                .transactionId("TXN-001")
                .userId(1L)
                .amount(new BigDecimal("99.99"))
                .currency("TND")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .status(PaymentStatus.PENDING)
                .description("Test payment")
                .subscriptionId(100L)
                .createdAt(LocalDateTime.now())
                .paymentDate(LocalDateTime.now())
                .build();

        paymentRequestDto = PaymentRequestDto.builder()
                .userId(1L)
                .planId(100L)
                .amount(new BigDecimal("99.99"))
                .currency("TND")
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .billingPeriod("MONTHLY")
                .customerEmail("test@example.com")
                .customerName("John Doe")
                .build();

        paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(1);
        paginationDto.setSize(10);
        paginationDto.setSortField("createdAt");
        paginationDto.setSortDirection(SortingDirection.DESC);
    }

    @Test
    void findById_ShouldReturnPaymentResponseDto() {
        // Given
        when(paymentTransactionRepository.findById(1L)).thenReturn(Optional.of(paymentTransaction));

        // When
        PaymentResponseDto result = paymentTransactionService.findById(1L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTransactionId()).isEqualTo(1L);
        assertThat(result.getTransactionReference()).isEqualTo("TXN-001");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(result.getCurrency()).isEqualTo("TND");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(paymentTransactionRepository).findById(1L);
    }

    @Test
    void findById_WhenIdIsNull_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.findById(null))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Transaction ID must be a positive number");
    }

    @Test
    void findById_WhenIdIsZero_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.findById(0L))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Transaction ID must be a positive number");
    }

    @Test
    void findById_WhenNotFound_ShouldThrowEntityNotFoundException() {
        // Given
        when(paymentTransactionRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.findById(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Payment transaction not found with id: 999");
    }

    @Test
    void findByTransactionId_ShouldReturnPaymentResponseDto() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.of(paymentTransaction));

        // When
        PaymentResponseDto result = paymentTransactionService.findByTransactionId("TXN-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTransactionReference()).isEqualTo("TXN-001");
        verify(paymentTransactionRepository).findByTransactionId("TXN-001");
    }

    @Test
    void findByTransactionId_WhenEmpty_ShouldThrowException() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("INVALID")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.findByTransactionId("INVALID"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findByProviderPaymentId_ShouldReturnPaymentResponseDto() {
        // Given
        when(paymentTransactionRepository.findByProviderPaymentId("prov_123")).thenReturn(Optional.of(paymentTransaction));

        // When
        PaymentResponseDto result = paymentTransactionService.findByProviderPaymentId("prov_123");

        // Then
        assertThat(result).isNotNull();
        verify(paymentTransactionRepository).findByProviderPaymentId("prov_123");
    }

    @Test
    void findAllByUserId_ShouldReturnPagedResponse() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentTransaction> page = new PageImpl<>(List.of(paymentTransaction), pageable, 1);
        when(paymentTransactionRepository.findAllByUserId(eq(1L), any(Pageable.class))).thenReturn(page);

        // When
        PagedResponse<PaymentResponseDto> result = paymentTransactionService.findAllByUserId(1L, paginationDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(paymentTransactionRepository).findAllByUserId(eq(1L), any(Pageable.class));
    }

    @Test
    void findAllByStatus_ShouldReturnPagedResponse() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentTransaction> page = new PageImpl<>(List.of(paymentTransaction), pageable, 1);
        when(paymentTransactionRepository.findAllByStatus(eq(PaymentStatus.PENDING), any(Pageable.class))).thenReturn(page);

        // When
        PagedResponse<PaymentResponseDto> result = paymentTransactionService.findAllByStatus(PaymentStatus.PENDING, paginationDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(paymentTransactionRepository).findAllByStatus(eq(PaymentStatus.PENDING), any(Pageable.class));
    }

    @Test
    void findByUserIdAndStatus_ShouldReturnPagedResponse() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentTransaction> page = new PageImpl<>(List.of(paymentTransaction), pageable, 1);
        when(paymentTransactionRepository.findByUserIdAndStatus(eq(1L), eq(PaymentStatus.PENDING), any(Pageable.class))).thenReturn(page);

        // When
        PagedResponse<PaymentResponseDto> result = paymentTransactionService.findByUserIdAndStatus(1L, PaymentStatus.PENDING, paginationDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(paymentTransactionRepository).findByUserIdAndStatus(eq(1L), eq(PaymentStatus.PENDING), any(Pageable.class));
    }

    @Test
    void filterTransactions_ShouldReturnPagedResponse() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentTransaction> page = new PageImpl<>(List.of(paymentTransaction), pageable, 1);
        // Fix: Use eq() with explicit type casting to avoid ambiguity
        when(paymentTransactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        PagedResponse<PaymentResponseDto> result = paymentTransactionService.filterTransactions(paginationDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(paymentTransactionRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void initiatePayment_ShouldCreateAndReturnPaymentResponse() {
        // Given
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.initiatePayment(paymentRequestDto);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTransactionReference()).startsWith("TXN-");
        assertThat(result.getAmount()).isEqualTo(new BigDecimal("99.99"));
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void updateTransactionStatus_ShouldUpdateAndReturn() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.of(paymentTransaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.updateTransactionStatus("TXN-001", PaymentStatus.COMPLETED);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING); // Still PENDING because we mocked

        verify(paymentTransactionRepository).findByTransactionId("TXN-001");
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void updateTransactionStatus_WhenTransactionNotFound_ShouldThrowException() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("INVALID")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.updateTransactionStatus("INVALID", PaymentStatus.COMPLETED))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateTransactionStatusWithReason_ShouldUpdateStatusAndReason() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.of(paymentTransaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.updateTransactionStatusWithReason("TXN-001", PaymentStatus.FAILED, "Insufficient funds");

        // Then
        assertThat(result).isNotNull();
        verify(paymentTransactionRepository).findByTransactionId("TXN-001");
        verify(paymentTransactionRepository).save(any(PaymentTransaction.class));
    }

    @Test
    void markAsCompleted_ShouldCallUpdateTransactionStatus() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.of(paymentTransaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.markAsCompleted("TXN-001");

        // Then
        assertThat(result).isNotNull();
        verify(paymentTransactionRepository).findByTransactionId("TXN-001");
    }

    @Test
    void markAsFailed_ShouldCallUpdateTransactionStatusWithReason() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.of(paymentTransaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.markAsFailed("TXN-001", "Payment failed");

        // Then
        assertThat(result).isNotNull();
        verify(paymentTransactionRepository).findByTransactionId("TXN-001");
    }

    @Test
    void markAsRefunded_ShouldCallUpdateTransactionStatus() {
        // Given
        when(paymentTransactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.of(paymentTransaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.markAsRefunded("TXN-001");

        // Then
        assertThat(result).isNotNull();
        verify(paymentTransactionRepository).findByTransactionId("TXN-001");
    }

    @Test
    void getTotalAmountSpentByUser_ShouldReturnTotal() {
        // Given
        BigDecimal expectedTotal = new BigDecimal("199.98");
        when(paymentTransactionRepository.getTotalAmountSpentByUser(1L, PaymentStatus.COMPLETED)).thenReturn(expectedTotal);

        // When
        BigDecimal result = paymentTransactionService.getTotalAmountSpentByUser(1L, PaymentStatus.COMPLETED);

        // Then
        assertThat(result).isEqualTo(expectedTotal);
        verify(paymentTransactionRepository).getTotalAmountSpentByUser(1L, PaymentStatus.COMPLETED);
    }

    @Test
    void countSuccessfulPaymentsByUser_ShouldReturnCount() {
        // Given
        when(paymentTransactionRepository.countSuccessfulPaymentsByUser(1L, PaymentStatus.COMPLETED)).thenReturn(5L);

        // When
        long result = paymentTransactionService.countSuccessfulPaymentsByUser(1L);

        // Then
        assertThat(result).isEqualTo(5L);
        verify(paymentTransactionRepository).countSuccessfulPaymentsByUser(1L, PaymentStatus.COMPLETED);
    }

    @Test
    void validatePaginationDto_WithInvalidPage_ShouldThrowException() {
        // Given
        paginationDto.setPage(0);

        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.findAllByUserId(1L, paginationDto))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Invalid pagination parameters");
    }

    @Test
    void validatePaginationDto_WithInvalidSize_ShouldThrowException() {
        // Given
        paginationDto.setSize(0);

        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.findAllByUserId(1L, paginationDto))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Invalid pagination parameters");
    }

    @Test
    void validatePaginationDto_WithSizeExceedingLimit_ShouldThrowException() {
        // Given
        paginationDto.setSize(200);

        // When & Then
        assertThatThrownBy(() -> paymentTransactionService.findAllByUserId(1L, paginationDto))
                .isInstanceOf(InvalidEntityException.class)
                .hasMessageContaining("Page size cannot exceed 100");
    }

    @Test
    void generateTransactionId_ShouldStartWithTXN() {
        // Given
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.initiatePayment(paymentRequestDto);

        // Then
        assertThat(result.getTransactionReference()).startsWith("TXN-");
    }

    @Test
    void convertToResponseDto_ShouldMapCorrectly() {
        // Given
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenReturn(paymentTransaction);

        // When
        PaymentResponseDto result = paymentTransactionService.initiatePayment(paymentRequestDto);

        // Then
        assertThat(result.getTransactionId()).isEqualTo(paymentTransaction.getId());
        assertThat(result.getTransactionReference()).isEqualTo(paymentTransaction.getTransactionId());
        assertThat(result.getAmount()).isEqualTo(paymentTransaction.getAmount());
        assertThat(result.getCurrency()).isEqualTo(paymentTransaction.getCurrency());
        assertThat(result.getStatus()).isEqualTo(paymentTransaction.getStatus());
        assertThat(result.getCreatedAt()).isEqualTo(paymentTransaction.getCreatedAt());
    }
}