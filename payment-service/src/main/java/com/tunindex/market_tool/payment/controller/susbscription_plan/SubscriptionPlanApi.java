package com.tunindex.market_tool.payment.controller.susbscription_plan;

import com.tunindex.market_tool.common.utils.pagination.PaginationAndFilteringDto;
import com.tunindex.market_tool.common.utils.pagination.response.PagedResponse;
import com.tunindex.market_tool.payment.dto.SubscriptionPlanDto;
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

import java.math.BigDecimal;

@Tag(name = "Subscription Plans", description = "API for managing subscription plans")
@Validated
public interface SubscriptionPlanApi {

    String BASE_URL = "/api/subscription-plans";

    // ==================== GET ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get subscription plan by ID",
            description = "Retrieves a subscription plan by its unique identifier"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan found successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid ID format", content = @Content)
    })
    ResponseEntity<SubscriptionPlanDto> getPlanById(
            @Parameter(description = "Plan ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    @GetMapping(value = BASE_URL + "/name/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get subscription plan by name",
            description = "Retrieves a subscription plan by its name"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan found successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid name", content = @Content)
    })
    ResponseEntity<SubscriptionPlanDto> getPlanByName(
            @Parameter(description = "Plan name", required = true, example = "BASIC")
            @PathVariable @NotNull String name
    );

    @GetMapping(value = BASE_URL + "/active", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get all active plans",
            description = "Retrieves paginated list of all active subscription plans"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<SubscriptionPlanDto>> getActivePlans(
            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size,

            @Parameter(description = "Sort field", example = "displayOrder")
            @RequestParam(defaultValue = "displayOrder") String sortField,

            @Parameter(description = "Sort direction (ASC or DESC)", example = "ASC")
            @RequestParam(defaultValue = "ASC") String sortDirection
    );

    @GetMapping(value = BASE_URL + "/price-range", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get plans by max price",
            description = "Retrieves paginated list of active plans with monthly price <= maxPrice"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid price or pagination parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<SubscriptionPlanDto>> getPlansByMaxPrice(
            @Parameter(description = "Maximum monthly price", required = true, example = "29.99")
            @RequestParam @NotNull BigDecimal maxPrice,

            @Parameter(description = "Page number (1-indexed)", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int page,

            @Parameter(description = "Page size (max 100)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) int size
    );

    // ==================== POST ENDPOINTS ====================

    @PostMapping(value = BASE_URL, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create a new subscription plan",
            description = "Creates a new subscription plan"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid plan data", content = @Content),
            @ApiResponse(responseCode = "409", description = "Plan with same name already exists", content = @Content)
    })
    ResponseEntity<SubscriptionPlanDto> createPlan(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Subscription plan data",
                    required = true,
                    content = @Content(
                            examples = {
                                    @ExampleObject(
                                            name = "Basic Plan",
                                            value = """
                                            {
                                                "name": "BASIC",
                                                "description": "Basic subscription plan for individual users",
                                                "priceMonthly": 9.99,
                                                "priceYearly": 99.99,
                                                "currency": "TND",
                                                "durationDays": 30,
                                                "features": "Basic stock data, Email support",
                                                "apiCallsLimit": 1000,
                                                "displayOrder": 1
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Pro Plan",
                                            value = """
                                            {
                                                "name": "PRO",
                                                "description": "Professional subscription plan for traders",
                                                "priceMonthly": 29.99,
                                                "priceYearly": 299.99,
                                                "currency": "TND",
                                                "durationDays": 30,
                                                "features": "Advanced stock data, Graham calculator, Priority support",
                                                "apiCallsLimit": 10000,
                                                "displayOrder": 2
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Enterprise Plan",
                                            value = """
                                            {
                                                "name": "ENTERPRISE",
                                                "description": "Enterprise subscription plan for institutions",
                                                "priceMonthly": 99.99,
                                                "priceYearly": 999.99,
                                                "currency": "TND",
                                                "durationDays": 30,
                                                "features": "All Pro features, Dedicated support, Custom integration",
                                                "apiCallsLimit": 100000,
                                                "displayOrder": 3
                                            }
                                            """
                                    )
                            }
                    )
            )
            @Valid @RequestBody SubscriptionPlanDto planDto
    );

    @PostMapping(value = BASE_URL + "/filter", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Filter subscription plans",
            description = """
                    Filter subscription plans using multiple criteria including:
                    - name: Search by plan name (partial match)
                    - isActive: Filter by active status
                    - minMonthlyPrice/maxMonthlyPrice: Filter by monthly price range
                    - minYearlyPrice/maxYearlyPrice: Filter by yearly price range
                    - minApiCallsLimit: Filter by minimum API calls limit
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plans filtered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters", content = @Content)
    })
    ResponseEntity<PagedResponse<SubscriptionPlanDto>> filterPlans(
            @Valid @RequestBody PaginationAndFilteringDto paginationDto
    );

    // ==================== PUT ENDPOINTS ====================

    @PutMapping(value = BASE_URL + "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Update subscription plan",
            description = "Updates an existing subscription plan"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan updated successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @ApiResponse(responseCode = "400", description = "Invalid plan data", content = @Content),
            @ApiResponse(responseCode = "409", description = "Plan with same name already exists", content = @Content)
    })
    ResponseEntity<SubscriptionPlanDto> updatePlan(
            @Parameter(description = "Plan ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated plan data",
                    required = true
            )
            @Valid @RequestBody SubscriptionPlanDto planDto
    );

    @PutMapping(value = BASE_URL + "/{id}/toggle-status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Toggle plan active status",
            description = "Activates or deactivates a subscription plan"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Plan status toggled successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found", content = @Content)
    })
    ResponseEntity<SubscriptionPlanDto> togglePlanStatus(
            @Parameter(description = "Plan ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    // ==================== DELETE ENDPOINTS ====================

    @DeleteMapping(value = BASE_URL + "/{id}")
    @Operation(
            summary = "Delete subscription plan",
            description = "Deletes a subscription plan (only if not in use)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Plan deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Plan not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Plan is in use", content = @Content)
    })
    ResponseEntity<Void> deletePlan(
            @Parameter(description = "Plan ID", required = true, example = "1")
            @PathVariable @NotNull @Positive Long id
    );

    // ==================== VALIDATION ENDPOINTS ====================

    @GetMapping(value = BASE_URL + "/check-name/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Check if plan name exists",
            description = "Checks whether a subscription plan with the given name already exists"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Check completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid name", content = @Content)
    })
    ResponseEntity<Boolean> checkNameExists(
            @Parameter(description = "Plan name", required = true, example = "BASIC")
            @PathVariable @NotNull String name
    );
}