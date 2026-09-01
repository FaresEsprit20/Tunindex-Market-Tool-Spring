package com.tunindex.market_tool.payment.service.susbscription_plan;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.SubscriptionPlanDto;

import java.math.BigDecimal;

public interface SubscriptionPlanService {

    SubscriptionPlanDto findById(Long id);

    SubscriptionPlanDto findByName(String name);

    SubscriptionPlanDto findByNameAndIsActiveTrue(String name);

    PagedResponse<SubscriptionPlanDto> findAllActiveOrderByDisplayOrder(PaginationAndFilteringDto paginationDto);

    PagedResponse<SubscriptionPlanDto> findAllActiveOrderByPriceMonthly(PaginationAndFilteringDto paginationDto);

    PagedResponse<SubscriptionPlanDto> findActivePlansByMaxPrice(BigDecimal maxPrice, PaginationAndFilteringDto paginationDto);

    PagedResponse<SubscriptionPlanDto> findByApiCallsLimitGreaterThanEqual(Integer minLimit, PaginationAndFilteringDto paginationDto);

    PagedResponse<SubscriptionPlanDto> filterPlans(PaginationAndFilteringDto paginationDto);

    SubscriptionPlanDto createPlan(SubscriptionPlanDto planDto);

    SubscriptionPlanDto updatePlan(Long id, SubscriptionPlanDto planDto);

    SubscriptionPlanDto togglePlanStatus(Long id);

    void deletePlan(Long id);

    boolean existsByName(String name);
}