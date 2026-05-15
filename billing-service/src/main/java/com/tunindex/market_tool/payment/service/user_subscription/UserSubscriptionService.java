package com.tunindex.market_tool.payment.service.user_subscription;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.UserSubscriptionDto;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;

import java.time.LocalDateTime;

public interface UserSubscriptionService {

    UserSubscriptionDto findById(Long id);

    UserSubscriptionDto findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    UserSubscriptionDto findActiveSubscriptionByUserId(Long userId);

    PagedResponse<UserSubscriptionDto> findAllByUserId(Long userId, PaginationAndFilteringDto paginationDto);

    PagedResponse<UserSubscriptionDto> findExpiredSubscriptions(PaginationAndFilteringDto paginationDto);

    PagedResponse<UserSubscriptionDto> findSubscriptionsExpiringBetween(LocalDateTime start, LocalDateTime end, PaginationAndFilteringDto paginationDto);

    PagedResponse<UserSubscriptionDto> filterSubscriptions(PaginationAndFilteringDto paginationDto);

    UserSubscriptionDto createSubscription(UserSubscriptionDto subscriptionDto);

    UserSubscriptionDto cancelSubscription(Long id, String reason);

    UserSubscriptionDto renewSubscription(Long id);

    UserSubscriptionDto updateSubscriptionStatus(Long id, SubscriptionStatus newStatus);

    long countActiveSubscriptionsByUser(Long userId);

    void deleteSubscription(Long id);
}