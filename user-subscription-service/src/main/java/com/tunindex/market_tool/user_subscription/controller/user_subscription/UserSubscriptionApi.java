package com.tunindex.market_tool.user_subscription.controller.user_subscription;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.user_subscription.dto.UserSubscriptionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    ResponseEntity<PagedResponse<UserSubscriptionDto>> getExpiredSubscriptions(int page, int size);

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


}