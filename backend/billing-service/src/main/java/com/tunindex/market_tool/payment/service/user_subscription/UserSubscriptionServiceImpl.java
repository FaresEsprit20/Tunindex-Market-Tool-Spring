package com.tunindex.market_tool.payment.service.user_subscription;

import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationUtil;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.UserSubscriptionDto;
import com.tunindex.market_tool.payment.entities.SubscriptionPlan;
import com.tunindex.market_tool.payment.entities.UserSubscription;
import com.tunindex.market_tool.payment.entities.enums.BillingPeriod;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import com.tunindex.market_tool.payment.repository.SubscriptionPlanRepository;
import com.tunindex.market_tool.payment.repository.UserSubscriptionRepository;
import com.tunindex.market_tool.payment.specifications.UserSubscriptionSpecification;
import com.tunindex.market_tool.payment.validators.UserSubscriptionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSubscriptionServiceImpl implements UserSubscriptionService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    @Transactional(readOnly = true)
    public UserSubscriptionDto findById(Long id) {
        log.info("🔍 Finding user subscription by id: {}", id);

        List<String> errors = new ArrayList<>();

        if (id == null || id <= 0) {
            errors.add("Subscription ID must be a positive number");
            throw new InvalidEntityException("Invalid subscription ID", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }

        return userSubscriptionRepository.findById(id)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No subscription found with id: " + id);
                    return new EntityNotFoundException(
                            "Subscription not found with id: " + id,
                            ErrorCodes.SUBSCRIPTION_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UserSubscriptionDto findByUserIdAndStatus(Long userId, SubscriptionStatus status) {
        log.info("🔍 Finding subscription for user: {} with status: {}", userId, status);

        List<String> errors = new ArrayList<>();

        if (userId == null || userId <= 0) {
            errors.add("User ID must be a positive number");
            throw new InvalidEntityException("Invalid user ID", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }

        if (status == null) {
            errors.add("Status cannot be null");
            throw new InvalidEntityException("Invalid status", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }

        return userSubscriptionRepository.findByUserIdAndStatus(userId, status)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No subscription found for user: " + userId + " with status: " + status);
                    return new EntityNotFoundException(
                            "Subscription not found",
                            ErrorCodes.SUBSCRIPTION_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public UserSubscriptionDto findActiveSubscriptionByUserId(Long userId) {
        log.info("🔍 Finding active subscription for user: {}", userId);

        List<String> errors = new ArrayList<>();

        if (userId == null || userId <= 0) {
            errors.add("User ID must be a positive number");
            throw new InvalidEntityException("Invalid user ID", ErrorCodes.SUBSCRIPTION_NOT_FOUND, errors);
        }

        LocalDateTime now = LocalDateTime.now();
        return userSubscriptionRepository.findActiveSubscriptionByUserId(userId, SubscriptionStatus.ACTIVE, now)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No active subscription found for user: " + userId);
                    return new EntityNotFoundException(
                            "Active subscription not found for user: " + userId,
                            ErrorCodes.SUBSCRIPTION_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserSubscriptionDto> findAllByUserId(Long userId, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all subscriptions for user: {} with pagination", userId);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<UserSubscription> subscriptionPage = userSubscriptionRepository.findAllByUserId(userId, pageable);

        return buildPagedResponse(subscriptionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserSubscriptionDto> findExpiredSubscriptions(PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding expired subscriptions");

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        LocalDateTime now = LocalDateTime.now();
        Page<UserSubscription> subscriptionPage = userSubscriptionRepository.findExpiredSubscriptions(now, SubscriptionStatus.EXPIRED, pageable);

        return buildPagedResponse(subscriptionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserSubscriptionDto> findSubscriptionsExpiringBetween(LocalDateTime start, LocalDateTime end, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding subscriptions expiring between {} and {}", start, end);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<UserSubscription> subscriptionPage = userSubscriptionRepository.findSubscriptionsExpiringBetween(start, end, SubscriptionStatus.ACTIVE, pageable);

        return buildPagedResponse(subscriptionPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<UserSubscriptionDto> filterSubscriptions(PaginationAndFilteringDto paginationDto) {
        log.info("🔍 Filtering subscriptions with pagination: page={}, size={}, filters={}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        validatePaginationDto(paginationDto);

        Specification<UserSubscription> specification = buildSpecificationFromFilters(paginationDto.getFilters());
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<UserSubscription> subscriptionPage = userSubscriptionRepository.findAll(specification, pageable);

        return buildPagedResponse(subscriptionPage);
    }

    @Override
    @Transactional
    public UserSubscriptionDto createSubscription(UserSubscriptionDto subscriptionDto) {
        log.info("📝 Creating new subscription for user: {}", subscriptionDto.getUserId());

        UserSubscriptionValidator.validate(subscriptionDto);

        LocalDateTime now = LocalDateTime.now();
        if (userSubscriptionRepository.findActiveSubscriptionByUserId(subscriptionDto.getUserId(), SubscriptionStatus.ACTIVE, now).isPresent()) {
            List<String> errors = new ArrayList<>();
            errors.add("User already has an active subscription");
            throw new InvalidEntityException(
                    "User already has an active subscription",
                    ErrorCodes.SUBSCRIPTION_ALREADY_ACTIVE,
                    errors
            );
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findById(subscriptionDto.getPlanId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription plan not found with id: " + subscriptionDto.getPlanId(),
                        ErrorCodes.PLAN_NOT_FOUND,
                        List.of("No plan found")
                ));

        UserSubscription subscription = convertToEntity(subscriptionDto);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());

        if (plan.getDurationDays() != null) {
            subscription.setEndDate(LocalDateTime.now().plusDays(plan.getDurationDays()));
        } else if (subscriptionDto.getBillingPeriod() == BillingPeriod.MONTHLY) {
            subscription.setEndDate(LocalDateTime.now().plusMonths(1));
        } else if (subscriptionDto.getBillingPeriod() == BillingPeriod.YEARLY) {
            subscription.setEndDate(LocalDateTime.now().plusYears(1));
        } else {
            subscription.setEndDate(LocalDateTime.now().plusMonths(1));
        }

        UserSubscription savedSubscription = userSubscriptionRepository.save(subscription);

        log.info("✅ Subscription created successfully for user: {} with end date: {}",
                savedSubscription.getUserId(), savedSubscription.getEndDate());
        return convertToDto(savedSubscription);
    }

    @Override
    @Transactional
    public UserSubscriptionDto cancelSubscription(Long id, String reason) {
        log.info("❌ Cancelling subscription with id: {}", id);

        UserSubscription subscription = userSubscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription not found with id: " + id,
                        ErrorCodes.SUBSCRIPTION_NOT_FOUND,
                        List.of("No subscription found")
                ));

        UserSubscriptionValidator.validateCancellation(subscription, reason);

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        subscription.setCancellationReason(reason);

        UserSubscription updatedSubscription = userSubscriptionRepository.save(subscription);
        log.info("✅ Subscription cancelled successfully for user: {}", updatedSubscription.getUserId());
        return convertToDto(updatedSubscription);
    }

    @Override
    @Transactional
    public UserSubscriptionDto renewSubscription(Long id) {
        log.info("🔄 Renewing subscription with id: {}", id);

        UserSubscription subscription = userSubscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription not found with id: " + id,
                        ErrorCodes.SUBSCRIPTION_NOT_FOUND,
                        List.of("No subscription found")
                ));

        UserSubscriptionValidator.validateRenewal(subscription);

        LocalDateTime newEndDate;
        if (subscription.getBillingPeriod() == BillingPeriod.YEARLY) {
            newEndDate = LocalDateTime.now().plusYears(1);
        } else {
            newEndDate = LocalDateTime.now().plusMonths(1);
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartDate(LocalDateTime.now());
        subscription.setEndDate(newEndDate);
        subscription.setCancelledAt(null);
        subscription.setCancellationReason(null);

        UserSubscription updatedSubscription = userSubscriptionRepository.save(subscription);
        log.info("✅ Subscription renewed successfully for user: {} until: {}",
                updatedSubscription.getUserId(), updatedSubscription.getEndDate());
        return convertToDto(updatedSubscription);
    }

    @Override
    @Transactional
    public UserSubscriptionDto updateSubscriptionStatus(Long id, SubscriptionStatus newStatus) {
        log.info("🔄 Updating subscription status for id: {} to: {}", id, newStatus);

        UserSubscription subscription = userSubscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription not found with id: " + id,
                        ErrorCodes.SUBSCRIPTION_NOT_FOUND,
                        List.of("No subscription found")
                ));

        subscription.setStatus(newStatus);

        if (newStatus == SubscriptionStatus.CANCELLED) {
            subscription.setCancelledAt(LocalDateTime.now());
        }

        UserSubscription updatedSubscription = userSubscriptionRepository.save(subscription);
        log.info("✅ Subscription status updated successfully");
        return convertToDto(updatedSubscription);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveSubscriptionsByUser(Long userId) {
        log.info("📊 Counting active subscriptions for user: {}", userId);
        return userSubscriptionRepository.countByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
    }

    @Override
    @Transactional
    public void deleteSubscription(Long id) {
        log.info("🗑️ Deleting subscription with id: {}", id);

        UserSubscription subscription = userSubscriptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription not found with id: " + id,
                        ErrorCodes.SUBSCRIPTION_NOT_FOUND,
                        List.of("No subscription found")
                ));

        userSubscriptionRepository.delete(subscription);
        log.info("✅ Subscription deleted successfully");
    }

    // ========== PRIVATE HELPER METHODS ==========

    private void validatePaginationDto(PaginationAndFilteringDto paginationDto) {
        List<String> errors = new ArrayList<>();

        if (paginationDto.getPage() == null || paginationDto.getPage() < 1) {
            errors.add("Page number must be greater than 0");
        }

        if (paginationDto.getSize() == null || paginationDto.getSize() < 1) {
            errors.add("Page size must be greater than 0");
        }

        if (paginationDto.getSize() != null && paginationDto.getSize() > 100) {
            errors.add("Page size cannot exceed 100");
        }

        if (!errors.isEmpty()) {
            throw new InvalidEntityException("Invalid pagination parameters", ErrorCodes.PAGE_NOT_VALID, errors);
        }
    }

    private PagedResponse<UserSubscriptionDto> buildPagedResponse(Page<UserSubscription> subscriptionPage) {
        List<UserSubscriptionDto> content = subscriptionPage.getContent()
                .stream()
                .map(this::convertToDto)
                .toList();

        return new PagedResponse<>(
                content,
                subscriptionPage.getNumber() + 1,
                subscriptionPage.getSize(),
                subscriptionPage.getTotalElements(),
                subscriptionPage.getTotalPages()
        );
    }

    private Specification<UserSubscription> buildSpecificationFromFilters(Map<String, String> filters) {
        Specification<UserSubscription> spec = UserSubscriptionSpecification.empty();

        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        if (StringUtils.hasLength(filters.get("userId"))) {
            try {
                Long userId = Long.valueOf(filters.get("userId"));
                spec = spec.and(UserSubscriptionSpecification.userIdEquals(userId));
            } catch (NumberFormatException e) {
                log.warn("Invalid userId value: {}", filters.get("userId"));
            }
        }

        if (StringUtils.hasLength(filters.get("status"))) {
            try {
                SubscriptionStatus status = SubscriptionStatus.valueOf(filters.get("status").toUpperCase());
                spec = spec.and(UserSubscriptionSpecification.statusEquals(status));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid status value: {}", filters.get("status"));
            }
        }

        if (StringUtils.hasLength(filters.get("billingPeriod"))) {
            try {
                BillingPeriod billingPeriod = BillingPeriod.valueOf(filters.get("billingPeriod").toUpperCase());
                spec = spec.and(UserSubscriptionSpecification.billingPeriodEquals(billingPeriod));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid billingPeriod value: {}", filters.get("billingPeriod"));
            }
        }

        if (filters.containsKey("autoRenew")) {
            Boolean autoRenew = Boolean.parseBoolean(filters.get("autoRenew"));
            spec = spec.and(UserSubscriptionSpecification.autoRenewEquals(autoRenew));
        }

        if (StringUtils.hasLength(filters.get("startDateFrom"))) {
            LocalDateTime from = parseLocalDateTime(filters.get("startDateFrom"));
            spec = spec.and(UserSubscriptionSpecification.startDateBetween(from, null));
        }

        if (StringUtils.hasLength(filters.get("startDateTo"))) {
            LocalDateTime to = parseLocalDateTime(filters.get("startDateTo"));
            spec = spec.and(UserSubscriptionSpecification.startDateBetween(null, to));
        }

        if (StringUtils.hasLength(filters.get("endDateFrom"))) {
            LocalDateTime from = parseLocalDateTime(filters.get("endDateFrom"));
            spec = spec.and(UserSubscriptionSpecification.endDateBetween(from, null));
        }

        if (StringUtils.hasLength(filters.get("endDateTo"))) {
            LocalDateTime to = parseLocalDateTime(filters.get("endDateTo"));
            spec = spec.and(UserSubscriptionSpecification.endDateBetween(null, to));
        }

        return spec;
    }

    private LocalDateTime parseLocalDateTime(String value) {
        if (!StringUtils.hasLength(value)) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            log.warn("Invalid date format for filter: {}", value);
            return null;
        }
    }

    private UserSubscriptionDto convertToDto(UserSubscription subscription) {
        return UserSubscriptionDto.builder()
                .id(subscription.getId())
                .userId(subscription.getUserId())
                .planId(subscription.getPlan() != null ? subscription.getPlan().getId() : null)
                .planName(subscription.getPlan() != null ? subscription.getPlan().getName() : null)
                .status(subscription.getStatus())
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .billingPeriod(subscription.getBillingPeriod())
                .autoRenew(subscription.getAutoRenew())
                .cancellationReason(subscription.getCancellationReason())
                .cancelledAt(subscription.getCancelledAt())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .build();
    }

    private UserSubscription convertToEntity(UserSubscriptionDto dto) {
        UserSubscription subscription = UserSubscription.builder()
                .id(dto.getId())
                .userId(dto.getUserId())
                .status(dto.getStatus())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .billingPeriod(dto.getBillingPeriod())
                .autoRenew(dto.getAutoRenew())
                .cancellationReason(dto.getCancellationReason())
                .cancelledAt(dto.getCancelledAt())
                .build();

        if (dto.getPlanId() != null) {
            SubscriptionPlan plan = subscriptionPlanRepository.findById(dto.getPlanId()).orElse(null);
            subscription.setPlan(plan);
        }

        return subscription;
    }
}