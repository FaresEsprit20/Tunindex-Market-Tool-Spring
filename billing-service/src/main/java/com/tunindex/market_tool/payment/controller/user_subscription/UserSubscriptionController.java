package com.tunindex.market_tool.payment.controller.user_subscription;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.enums.SortingDirection;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.UserSubscriptionDto;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import com.tunindex.market_tool.payment.service.user_subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserSubscriptionController implements UserSubscriptionApi {

    private final UserSubscriptionService userSubscriptionService;

    @Override
    public ResponseEntity<UserSubscriptionDto> getSubscriptionById(Long id) {
        log.info("GET /api/user-subscriptions/{}", id);
        UserSubscriptionDto subscription = userSubscriptionService.findById(id);
        return ResponseEntity.ok(subscription);
    }

    @Override
    public ResponseEntity<UserSubscriptionDto> getActiveSubscriptionByUser(Long userId) {
        log.info("GET /api/user-subscriptions/user/{}/active", userId);
        UserSubscriptionDto subscription = userSubscriptionService.findActiveSubscriptionByUserId(userId);
        return ResponseEntity.ok(subscription);
    }

    @Override
    public ResponseEntity<PagedResponse<UserSubscriptionDto>> getSubscriptionsByUser(
            Long userId, int page, int size, String sortField, String sortDirection) {

        log.info("GET /api/user-subscriptions/user/{} - page: {}, size: {}, sortField: {}, sortDirection: {}",
                userId, page, size, sortField, sortDirection);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField(sortField);
        paginationDto.setSortDirection(SortingDirection.valueOf(sortDirection.toUpperCase()));

        PagedResponse<UserSubscriptionDto> response = userSubscriptionService.findAllByUserId(userId, paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<UserSubscriptionDto>> getExpiredSubscriptions(int page, int size) {
        log.info("GET /api/user-subscriptions/expired - page: {}, size: {}", page, size);

        PaginationAndFilteringDto paginationDto = new PaginationAndFilteringDto();
        paginationDto.setPage(page);
        paginationDto.setSize(size);
        paginationDto.setSortField("endDate");
        paginationDto.setSortDirection(SortingDirection.ASC);

        PagedResponse<UserSubscriptionDto> response = userSubscriptionService.findExpiredSubscriptions(paginationDto);
        return ResponseEntity.ok(response);
    }


    @Override
    public ResponseEntity<PagedResponse<UserSubscriptionDto>> filterSubscriptions(PaginationAndFilteringDto paginationDto) {
        log.info("POST /api/user-subscriptions/filter - page: {}, size: {}, filters: {}",
                paginationDto.getPage(), paginationDto.getSize(), paginationDto.getFilters());

        PagedResponse<UserSubscriptionDto> response = userSubscriptionService.filterSubscriptions(paginationDto);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserSubscriptionDto> cancelSubscription(Long id, String reason) {
        log.info("PUT /api/user-subscriptions/{}/cancel - Reason: {}", id, reason);

        UserSubscriptionDto cancelledSubscription = userSubscriptionService.cancelSubscription(id, reason);
        return ResponseEntity.ok(cancelledSubscription);
    }



}