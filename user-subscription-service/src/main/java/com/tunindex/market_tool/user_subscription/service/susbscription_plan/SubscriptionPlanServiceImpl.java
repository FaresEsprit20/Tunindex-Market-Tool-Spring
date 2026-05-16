package com.tunindex.market_tool.user_subscription.service.susbscription_plan;

import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidEntityException;
import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.PaginationUtil;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.entities.SubscriptionPlan;
import com.tunindex.market_tool.payment.repository.SubscriptionPlanRepository;
import com.tunindex.market_tool.user_subscription.dto.SubscriptionPlanDto;
import com.tunindex.market_tool.user_subscription.specifications.SubscriptionPlanSpecification;
import com.tunindex.market_tool.user_subscription.validators.SubscriptionPlanValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanServiceImpl implements SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanDto findById(Long id) {
        log.info("🔍 Finding subscription plan by id: {}", id);

        List<String> errors = new ArrayList<>();

        if (id == null || id <= 0) {
            errors.add("Plan ID must be a positive number");
            throw new InvalidEntityException("Invalid plan ID", ErrorCodes.PLAN_NOT_FOUND, errors);
        }

        return subscriptionPlanRepository.findById(id)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No subscription plan found with id: " + id);
                    return new EntityNotFoundException(
                            "Subscription plan not found with id: " + id,
                            ErrorCodes.PLAN_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanDto findByName(String name) {
        log.info("🔍 Finding subscription plan by name: {}", name);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(name)) {
            errors.add("Plan name cannot be empty");
            throw new InvalidEntityException("Invalid plan name", ErrorCodes.PLAN_NOT_FOUND, errors);
        }

        return subscriptionPlanRepository.findByName(name)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No subscription plan found with name: " + name);
                    return new EntityNotFoundException(
                            "Subscription plan not found with name: " + name,
                            ErrorCodes.PLAN_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPlanDto findByNameAndIsActiveTrue(String name) {
        log.info("🔍 Finding active subscription plan by name: {}", name);

        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasLength(name)) {
            errors.add("Plan name cannot be empty");
            throw new InvalidEntityException("Invalid plan name", ErrorCodes.PLAN_NOT_FOUND, errors);
        }

        return subscriptionPlanRepository.findByNameAndIsActiveTrue(name)
                .map(this::convertToDto)
                .orElseThrow(() -> {
                    errors.add("No active subscription plan found with name: " + name);
                    return new EntityNotFoundException(
                            "Active subscription plan not found with name: " + name,
                            ErrorCodes.PLAN_NOT_FOUND,
                            errors
                    );
                });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SubscriptionPlanDto> findAllActiveOrderByDisplayOrder(PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all active plans ordered by display order");

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<SubscriptionPlan> planPage = subscriptionPlanRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc(pageable);

        return buildPagedResponse(planPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SubscriptionPlanDto> findAllActiveOrderByPriceMonthly(PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding all active plans ordered by monthly price");

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<SubscriptionPlan> planPage = subscriptionPlanRepository.findAllByIsActiveTrueOrderByPriceMonthlyAsc(pageable);

        return buildPagedResponse(planPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SubscriptionPlanDto> findActivePlansByMaxPrice(BigDecimal maxPrice, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding active plans with max price: {}", maxPrice);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<SubscriptionPlan> planPage = subscriptionPlanRepository.findActivePlansByMaxPrice(maxPrice, pageable);

        return buildPagedResponse(planPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SubscriptionPlanDto> findByApiCallsLimitGreaterThanEqual(Integer minLimit, PaginationAndFilteringDto paginationDto) {
        log.info("📄 Finding plans with API calls limit >= {}", minLimit);

        validatePaginationDto(paginationDto);
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<SubscriptionPlan> planPage = subscriptionPlanRepository.findByApiCallsLimitGreaterThanEqual(minLimit, pageable);

        return buildPagedResponse(planPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SubscriptionPlanDto> filterPlans(PaginationAndFilteringDto paginationDto) {
        log.info("🔍 Filtering subscription plans with pagination: page={}, size={}, filters={}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        validatePaginationDto(paginationDto);

        Specification<SubscriptionPlan> specification = buildSpecificationFromFilters(paginationDto.getFilters());
        Pageable pageable = PaginationUtil.createPageRequest(paginationDto);
        Page<SubscriptionPlan> planPage = subscriptionPlanRepository.findAll(specification, pageable);

        return buildPagedResponse(planPage);
    }

    @Override
    @Transactional
    public SubscriptionPlanDto createPlan(SubscriptionPlanDto planDto) {
        log.info("📝 Creating new subscription plan: {}", planDto.getName());

        // Validate the plan
        SubscriptionPlanValidator.validate(planDto);

        // Check if plan with same name already exists
        if (subscriptionPlanRepository.existsByName(planDto.getName())) {
            List<String> errors = new ArrayList<>();
            errors.add("Plan with name '" + planDto.getName() + "' already exists");
            throw new InvalidEntityException(
                    "Subscription plan already exists",
                    ErrorCodes.PLAN_ALREADY_EXISTS,
                    errors
            );
        }

        SubscriptionPlan plan = convertToEntity(planDto);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setIsActive(true);

        SubscriptionPlan savedPlan = subscriptionPlanRepository.save(plan);

        log.info("✅ Subscription plan created successfully with id: {}", savedPlan.getId());
        return convertToDto(savedPlan);
    }

    @Override
    @Transactional
    public SubscriptionPlanDto updatePlan(Long id, SubscriptionPlanDto planDto) {
        log.info("🔄 Updating subscription plan with id: {}", id);

        SubscriptionPlan existingPlan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription plan not found with id: " + id,
                        ErrorCodes.PLAN_NOT_FOUND,
                        List.of("No plan found")
                ));

        SubscriptionPlanValidator.validateForUpdate(existingPlan, planDto);

        // Check name uniqueness (if name is being changed)
        if (!existingPlan.getName().equals(planDto.getName()) &&
                subscriptionPlanRepository.existsByName(planDto.getName())) {
            List<String> errors = new ArrayList<>();
            errors.add("Plan with name '" + planDto.getName() + "' already exists");
            throw new InvalidEntityException(
                    "Subscription plan name already exists",
                    ErrorCodes.PLAN_ALREADY_EXISTS,
                    errors
            );
        }

        // Update fields
        if (StringUtils.hasLength(planDto.getName())) {
            existingPlan.setName(planDto.getName());
        }
        if (StringUtils.hasLength(planDto.getDescription())) {
            existingPlan.setDescription(planDto.getDescription());
        }
        if (planDto.getPriceMonthly() != null) {
            existingPlan.setPriceMonthly(planDto.getPriceMonthly());
        }
        if (planDto.getPriceYearly() != null) {
            existingPlan.setPriceYearly(planDto.getPriceYearly());
        }
        if (StringUtils.hasLength(planDto.getCurrency())) {
            existingPlan.setCurrency(planDto.getCurrency());
        }
        if (planDto.getDurationDays() != null) {
            existingPlan.setDurationDays(planDto.getDurationDays());
        }
        if (StringUtils.hasLength(planDto.getFeatures())) {
            existingPlan.setFeatures(planDto.getFeatures());
        }
        if (planDto.getApiCallsLimit() != null) {
            existingPlan.setApiCallsLimit(planDto.getApiCallsLimit());
        }
        if (planDto.getDisplayOrder() != null) {
            existingPlan.setDisplayOrder(planDto.getDisplayOrder());
        }

        existingPlan.setUpdatedAt(LocalDateTime.now());

        SubscriptionPlan updatedPlan = subscriptionPlanRepository.save(existingPlan);
        log.info("✅ Subscription plan updated successfully");
        return convertToDto(updatedPlan);
    }

    @Override
    @Transactional
    public SubscriptionPlanDto togglePlanStatus(Long id) {
        log.info("🔄 Toggling subscription plan status for id: {}", id);

        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription plan not found with id: " + id,
                        ErrorCodes.PLAN_NOT_FOUND,
                        List.of("No plan found")
                ));

        plan.setIsActive(!plan.getIsActive());
        plan.setUpdatedAt(LocalDateTime.now());

        SubscriptionPlan updatedPlan = subscriptionPlanRepository.save(plan);
        log.info("✅ Subscription plan status toggled to: {}", updatedPlan.getIsActive());
        return convertToDto(updatedPlan);
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        log.info("🗑️ Deleting subscription plan with id: {}", id);

        SubscriptionPlan plan = subscriptionPlanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Subscription plan not found with id: " + id,
                        ErrorCodes.PLAN_NOT_FOUND,
                        List.of("No plan found")
                ));

        // Check if plan is being used by any active subscriptions
        // This would require checking UserSubscription repository

        subscriptionPlanRepository.delete(plan);
        log.info("✅ Subscription plan deleted successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByName(String name) {
        return subscriptionPlanRepository.existsByName(name);
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

    private PagedResponse<SubscriptionPlanDto> buildPagedResponse(Page<SubscriptionPlan> planPage) {
        List<SubscriptionPlanDto> content = planPage.getContent()
                .stream()
                .map(this::convertToDto)
                .toList();

        return new PagedResponse<>(
                content,
                planPage.getNumber() + 1,
                planPage.getSize(),
                planPage.getTotalElements(),
                planPage.getTotalPages()
        );
    }

    private Specification<SubscriptionPlan> buildSpecificationFromFilters(Map<String, String> filters) {
        Specification<SubscriptionPlan> spec = SubscriptionPlanSpecification.empty();

        if (filters == null || filters.isEmpty()) {
            return spec;
        }

        if (StringUtils.hasLength(filters.get("name"))) {
            spec = spec.and(SubscriptionPlanSpecification.nameContains(filters.get("name")));
        }

        if (filters.containsKey("isActive")) {
            Boolean isActive = Boolean.parseBoolean(filters.get("isActive"));
            spec = spec.and(SubscriptionPlanSpecification.isActive(isActive));
        }

        // Monthly price range filters
        if (filters.containsKey("minMonthlyPrice") || filters.containsKey("maxMonthlyPrice")) {
            BigDecimal minPrice = parseBigDecimal(filters, "minMonthlyPrice");
            BigDecimal maxPrice = parseBigDecimal(filters, "maxMonthlyPrice");
            spec = spec.and(SubscriptionPlanSpecification.priceMonthlyBetween(minPrice, maxPrice));
        }

        // Yearly price range filters
        if (filters.containsKey("minYearlyPrice") || filters.containsKey("maxYearlyPrice")) {
            BigDecimal minPrice = parseBigDecimal(filters, "minYearlyPrice");
            BigDecimal maxPrice = parseBigDecimal(filters, "maxYearlyPrice");
            spec = spec.and(SubscriptionPlanSpecification.priceYearlyBetween(minPrice, maxPrice));
        }

        // API calls limit filter
        if (StringUtils.hasLength(filters.get("minApiCallsLimit"))) {
            try {
                Integer minLimit = Integer.valueOf(filters.get("minApiCallsLimit"));
                spec = spec.and(SubscriptionPlanSpecification.apiCallsLimitGreaterThan(minLimit));
            } catch (NumberFormatException e) {
                log.warn("Invalid minApiCallsLimit value: {}", filters.get("minApiCallsLimit"));
            }
        }

        return spec;
    }

    private BigDecimal parseBigDecimal(Map<String, String> filters, String key) {
        String value = filters.get(key);
        if (!StringUtils.hasLength(value)) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric value for filter '{}': {}", key, value);
            return null;
        }
    }

    private SubscriptionPlanDto convertToDto(SubscriptionPlan plan) {
        return SubscriptionPlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .priceMonthly(plan.getPriceMonthly())
                .priceYearly(plan.getPriceYearly())
                .currency(plan.getCurrency())
                .durationDays(plan.getDurationDays())
                .features(plan.getFeatures())
                .apiCallsLimit(plan.getApiCallsLimit())
                .isActive(plan.getIsActive())
                .displayOrder(plan.getDisplayOrder())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private SubscriptionPlan convertToEntity(SubscriptionPlanDto dto) {
        return SubscriptionPlan.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .priceMonthly(dto.getPriceMonthly())
                .priceYearly(dto.getPriceYearly())
                .currency(dto.getCurrency())
                .durationDays(dto.getDurationDays())
                .features(dto.getFeatures())
                .apiCallsLimit(dto.getApiCallsLimit())
                .isActive(dto.getIsActive())
                .displayOrder(dto.getDisplayOrder())
                .build();
    }
}