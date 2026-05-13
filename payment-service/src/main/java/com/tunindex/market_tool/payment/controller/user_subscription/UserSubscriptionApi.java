package com.tunindex.market_tool.payment.controller.user_subscription;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.UserSubscriptionDto;
import com.tunindex.market_tool.payment.entities.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "User Subscriptions", description = "API for managing user subscriptions")
@Validated
public interface UserSubscriptionApi {

    String BASE_URL = "/api/user-subscriptions";

    // ==================== GET ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get subscription by ID",
            description = "Retrieves a user subscription by its unique identifier"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription found successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID format", content = @Content)
    })
    ResponseEntity<UserSubscriptionDto> getSubscriptionById(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    @GetMapping(value = BASE_URL + "/user/{userId}/active", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get active subscription for user",
            description = "Retrieves the active subscription for a specific user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Active subscription found"),
            @ApiResponse(responseCode = "404", description = "No active subscription found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid user ID", content = @Content)
    })
    ResponseEntity<UserSubscriptionDto> getActiveSubscriptionByUser(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId
    );

    @GetMapping(value = BASE_URL + "/user/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all subscriptions for user",
            description = "Retrieves paginated list of all subscriptions for a specific user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriptions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<UserSubscriptionDto>> getSubscriptionsByUser(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId,

            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field", example = "createdAt")
            @RequestParam(defaultValue = "createdAt") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDirection
    );

    @GetMapping(value = BASE_URL + "/expired", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get expired subscriptions",
            description = "Retrieves paginated list of expired subscriptions"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expired subscriptions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<UserSubscriptionDto>> getExpiredSubscriptions(
            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size
    );

    @GetMapping(value = BASE_URL + "/expiring", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get subscriptions expiring in date range",
            description = "Retrieves paginated list of subscriptions expiring between the given dates"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriptions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid date range or pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<UserSubscriptionDto>> getSubscriptionsExpiringBetween(
            @Parameter(description = "Start date (ISO format)", required = true, example = "2024-12-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,

            @Parameter(description = "End date (ISO format)", required = true, example = "2024-12-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,

            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size
    );

    // ==================== POST ENDPOINTS ====================

    @PostMapping(value = BASE_URL, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new subscription",
            description = "Creates a new subscription for a user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid subscription data", content = @Content),
            @ApiResponse(responseCode = "409", description = "User already has an active subscription", content = @Content)
    })
    ResponseEntity<UserSubscriptionDto> createSubscription(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Subscription data",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Monthly subscription",
                                            value = """
                                            {
                                                "userId": 1,
                                                "planId": 1,
                                                "billingPeriod": "MONTHLY",
                                                "autoRenew": true
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Yearly subscription",
                                            value = """
                                            {
                                                "userId": 1,
                                                "planId": 2,
                                                "billingPeriod": "YEARLY",
                                                "autoRenew": true
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody UserSubscriptionDto subscriptionDto
    );

    @PostMapping(value = BASE_URL + "/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter subscriptions",
            description = """
                    Filter subscriptions using multiple criteria including:
                    - userId: Filter by user ID
                    - status: Filter by subscription status
                    - billingPeriod: Filter by billing period (MONTHLY/YEARLY)
                    - autoRenew: Filter by auto-renew setting
                    - startDateFrom/startDateTo: Filter by start date range
                    - endDateFrom/endDateTo: Filter by end date range
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscriptions filtered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<UserSubscriptionDto>> filterSubscriptions(
            @Valid @RequestBody PaginationAndFilteringDto paginationDto
    );

    // ==================== PUT ENDPOINTS ====================

    @PutMapping(value = BASE_URL + "/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Cancel subscription",
            description = "Cancels an active subscription"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Cannot cancel subscription", content = @Content)
    })
    ResponseEntity<UserSubscriptionDto> cancelSubscription(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Cancellation reason",
                    content = @Content(
                            examples = {
                                    @ExampleObject(value = """
                                    {
                                        "reason": "User requested cancellation"
                                    }
                                    """)
                            }
                    )
            )
            @RequestBody(required = false) String reason
    );

    @PutMapping(value = BASE_URL + "/{id}/renew", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Renew subscription",
            description = "Renews an expired or active subscription"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription renewed successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Cannot renew subscription", content = @Content)
    })
    ResponseEntity<UserSubscriptionDto> renewSubscription(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    @PutMapping(value = BASE_URL + "/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update subscription status",
            description = "Updates the status of a subscription"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription status updated successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid status transition", content = @Content)
    })
    ResponseEntity<UserSubscriptionDto> updateSubscriptionStatus(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id,

            @Parameter(description = "New subscription status", required = true,
                    schema = @Schema(allowableValues = {"ACTIVE", "EXPIRED", "CANCELLED", "PENDING", "TRIAL"}))
            @RequestParam @NotNull SubscriptionStatus status
    );

    // ==================== DELETE ENDPOINTS ====================

    @DeleteMapping(value = BASE_URL + "/{id}")
    @Operation(
            summary = "Delete subscription",
            description = "Deletes a subscription record (only if not active)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Subscription deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Cannot delete active subscription", content = @Content)
    })
    ResponseEntity<Void> deleteSubscription(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    // ==================== STATISTICS ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/statistics/count-active/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Count active subscriptions for user",
            description = "Returns the number of active subscriptions for a user"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Count retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid user ID", content = @Content)
    })
    ResponseEntity<Long> countActiveSubscriptionsByUser(
            @Parameter(description = "User ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long userId
    );
}