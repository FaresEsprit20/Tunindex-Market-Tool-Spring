package com.tunindex.market_tool.payment.controller.susbscription_plan;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.SubscriptionPlanDto;
import com.tunindex.market_tool.payment.service.susbscription_plan.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SubscriptionPlanController implements SubscriptionPlanApi {

    private final SubscriptionPlanService subscriptionPlanService;

    @Override
    public ResponseEntity<SubscriptionPlanDto> getPlanById(Long id) {
        log.info("GET /api/subscription-plans/{}", id);
        SubscriptionPlanDto plan = subscriptionPlanService.findById(id);
        return ResponseEntity.ok(plan);
    }

    @Override
    public ResponseEntity<SubscriptionPlanDto> getPlanByName(String name) {
        log.info("GET /api/subscription-plans/name/{}", name);
        SubscriptionPlanDto plan = subscriptionPlanService.findByName(name);
        return ResponseEntity.ok(plan);
    }

    @Override
    public ResponseEntity<PagedResponse<SubscriptionPlanDto>> getActivePlans(
            int page, int size, String sortField, String sortDirection) {

        log.info("GET /api/subscription-plans/active - page: {}, size: {}, sortField: {}, sortDirection: {}",
                page, size, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        PagedResponse<SubscriptionPlanDto> response = subscriptionPlanService.findAllActiveOrderByDisplayOrder(paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<SubscriptionPlanDto>> filterPlans(PaginationAndFilteringDto paginationDto) {
        log.info("POST /api/subscription-plans/filter - page: {}, size: {}, filters: {}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        PagedResponse<SubscriptionPlanDto> response = subscriptionPlanService.filterPlans(paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Boolean> checkNameExists(String name) {
        log.info("GET /api/subscription-plans/check-name/{}", name);

        boolean exists = subscriptionPlanService.existsByName(name);
        return ResponseEntity.ok(exists);
    }

}