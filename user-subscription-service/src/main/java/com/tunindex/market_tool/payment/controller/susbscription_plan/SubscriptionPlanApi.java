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

    // ==================== DELETE ENDPOINTS ====================

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