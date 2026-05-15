package com.tunindex.market_tool.api.services.promo;

import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromoCodeServiceImpl implements PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;

    @Override
    @Transactional(readOnly = true)
    public ApplyPromoCodeResponseDto applyPromoCode(ApplyPromoCodeRequestDto request) {
        log.info("Applying promo code: {} for amount: {}", request.getCode(), request.getAmount());

        List<String> errors = new ArrayList<>();

        PromoCode promo = promoCodeRepository.findValidPromoCode(request.getCode(), LocalDateTime.now())
                .orElseThrow(() -> new InvalidEntityException(
                        "Invalid or expired promo code",
                        ErrorCodes.PROMO_CODE_INVALID,
                        List.of("The promo code is invalid or has expired")
                ));

        // Check minimum purchase amount
        if (promo.getMinPurchaseAmount() != null &&
                request.getAmount().compareTo(promo.getMinPurchaseAmount()) < 0) {
            errors.add("Minimum purchase amount of " + promo.getMinPurchaseAmount() + " required");
            throw new InvalidEntityException(
                    "Minimum purchase amount not met",
                    ErrorCodes.PROMO_CODE_MIN_AMOUNT,
                    errors
            );
        }

        // Check if applicable to this plan
        if (promo.getApplicablePlanIds() != null && !promo.getApplicablePlanIds().isEmpty()) {
            String[] applicablePlans = promo.getApplicablePlanIds().split(",");
            boolean isApplicable = false;
            for (String planId : applicablePlans) {
                if (planId.equals(String.valueOf(request.getPlanId()))) {
                    isApplicable = true;
                    break;
                }
            }
            if (!isApplicable) {
                errors.add("This promo code is not applicable to the selected plan");
                throw new InvalidEntityException(
                        "Promo code not applicable",
                        ErrorCodes.PROMO_CODE_NOT_APPLICABLE,
                        errors
                );
            }
        }

        BigDecimal discountedAmount = calculateDiscountedAmount(promo, request.getAmount());
        BigDecimal savings = request.getAmount().subtract(discountedAmount);

        return ApplyPromoCodeResponseDto.builder()
                .valid(true)
                .code(promo.getCode())
                .discountType(promo.getDiscountType())
                .discountValue(promo.getDiscountValue())
                .originalAmount(request.getAmount())
                .discountedAmount(discountedAmount)
                .savings(savings)
                .message("Promo code applied successfully")
                .build();
    }

    @Override
    @Transactional
    public void usePromoCode(String code, Long transactionId) {
        log.info("Using promo code: {} for transaction: {}", code, transactionId);

        PromoCode promo = promoCodeRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Promo code not found",
                        ErrorCodes.PROMO_CODE_NOT_FOUND,
                        List.of("Promo code not found")
                ));

        promo.setUsedCount(promo.getUsedCount() + 1);
        promoCodeRepository.save(promo);
    }

    @Override
    @Transactional
    public PromoCodeDto createPromoCode(PromoCodeDto dto) {
        log.info("Creating promo code: {}", dto.getCode());

        if (promoCodeRepository.existsByCode(dto.getCode())) {
            throw new InvalidEntityException(
                    "Promo code already exists",
                    ErrorCodes.PROMO_CODE_ALREADY_EXISTS,
                    List.of("Code already exists")
            );
        }

        PromoCode promo = PromoCode.builder()
                .code(dto.getCode().toUpperCase())
                .description(dto.getDescription())
                .discountType(dto.getDiscountType())
                .discountValue(dto.getDiscountValue())
                .currency(dto.getCurrency())
                .minPurchaseAmount(dto.getMinPurchaseAmount())
                .maxDiscountAmount(dto.getMaxDiscountAmount())
                .usageLimit(dto.getUsageLimit())
                .isActive(true)
                .validFrom(dto.getValidFrom())
                .validUntil(dto.getValidUntil())
                .applicablePlanIds(dto.getApplicablePlanIds())
                .firstTimeOnly(dto.getFirstTimeOnly())
                .build();

        PromoCode saved = promoCodeRepository.save(promo);
        return convertToDto(saved);
    }

    @Override
    @Transactional
    public PromoCodeDto updatePromoCode(Long id, PromoCodeDto dto) {
        log.info("Updating promo code with id: {}", id);

        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Promo code not found",
                        ErrorCodes.PROMO_CODE_NOT_FOUND,
                        List.of("Promo code not found")
                ));

        promo.setDescription(dto.getDescription());
        promo.setDiscountType(dto.getDiscountType());
        promo.setDiscountValue(dto.getDiscountValue());
        promo.setMinPurchaseAmount(dto.getMinPurchaseAmount());
        promo.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        promo.setUsageLimit(dto.getUsageLimit());
        promo.setValidFrom(dto.getValidFrom());
        promo.setValidUntil(dto.getValidUntil());
        promo.setApplicablePlanIds(dto.getApplicablePlanIds());
        promo.setFirstTimeOnly(dto.getFirstTimeOnly());

        PromoCode updated = promoCodeRepository.save(promo);
        return convertToDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public PromoCodeDto findById(Long id) {
        log.info("Finding promo code by id: {}", id);

        return promoCodeRepository.findById(id)
                .map(this::convertToDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Promo code not found",
                        ErrorCodes.PROMO_CODE_NOT_FOUND,
                        List.of("Promo code not found")
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public PromoCodeDto findByCode(String code) {
        log.info("Finding promo code by code: {}", code);

        return promoCodeRepository.findByCode(code)
                .map(this::convertToDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Promo code not found",
                        ErrorCodes.PROMO_CODE_NOT_FOUND,
                        List.of("Promo code not found")
                ));
    }

    @Override
    @Transactional
    public void deletePromoCode(Long id) {
        log.info("Deleting promo code with id: {}", id);

        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Promo code not found",
                        ErrorCodes.PROMO_CODE_NOT_FOUND,
                        List.of("Promo code not found")
                ));

        promoCodeRepository.delete(promo);
    }

    @Override
    @Transactional
    public void deactivatePromoCode(Long id) {
        log.info("Deactivating promo code with id: {}", id);

        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Promo code not found",
                        ErrorCodes.PROMO_CODE_NOT_FOUND,
                        List.of("Promo code not found")
                ));

        promo.setIsActive(false);
        promoCodeRepository.save(promo);
    }

    // ========== PRIVATE HELPER METHODS ==========

    private BigDecimal calculateDiscountedAmount(PromoCode promo, BigDecimal originalAmount) {
        if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
            BigDecimal discount = originalAmount.multiply(promo.getDiscountValue().divide(new BigDecimal("100")));
            BigDecimal discounted = originalAmount.subtract(discount);

            if (promo.getMaxDiscountAmount() != null && discount.compareTo(promo.getMaxDiscountAmount()) > 0) {
                discounted = originalAmount.subtract(promo.getMaxDiscountAmount());
            }
            return discounted;
        } else {
            BigDecimal discounted = originalAmount.subtract(promo.getDiscountValue());
            return discounted.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : discounted;
        }
    }

    private PromoCodeDto convertToDto(PromoCode promo) {
        return PromoCodeDto.builder()
                .id(promo.getId())
                .code(promo.getCode())
                .description(promo.getDescription())
                .discountType(promo.getDiscountType())
                .discountValue(promo.getDiscountValue())
                .currency(promo.getCurrency())
                .minPurchaseAmount(promo.getMinPurchaseAmount())
                .maxDiscountAmount(promo.getMaxDiscountAmount())
                .usageLimit(promo.getUsageLimit())
                .usedCount(promo.getUsedCount())
                .isActive(promo.getIsActive())
                .validFrom(promo.getValidFrom())
                .validUntil(promo.getValidUntil())
                .applicablePlanIds(promo.getApplicablePlanIds())
                .firstTimeOnly(promo.getFirstTimeOnly())
                .createdAt(promo.getCreatedAt())
                .updatedAt(promo.getUpdatedAt())
                .build();
    }

}