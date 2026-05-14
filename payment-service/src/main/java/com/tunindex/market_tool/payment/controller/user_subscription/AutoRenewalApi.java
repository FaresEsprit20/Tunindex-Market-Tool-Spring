package com.tunindex.market_tool.payment.controller.user_subscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auto-Renewal", description = "API for managing subscription auto-renewal")
public interface AutoRenewalApi {

    String BASE_URL = "/api/auto-renewal";

    @PutMapping(value = BASE_URL + "/{subscriptionId}/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Toggle auto-renewal setting",
            description = "Enable or disable auto-renewal for a subscription"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Auto-renewal setting updated successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ResponseEntity<Map<String, Object>> toggleAutoRenewal(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable Long subscriptionId,

            @Parameter(description = "Enable or disable auto-renewal", required = true, example = "true")
            @RequestParam boolean enabled
    );

    @PostMapping(value = BASE_URL + "/{subscriptionId}/renew", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Manual renewal",
            description = "Manually renew a subscription immediately"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Subscription renewed successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "400", description = "Cannot renew subscription", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ResponseEntity<Map<String, Object>> manualRenewal(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable Long subscriptionId
    );

    @GetMapping(value = BASE_URL + "/{subscriptionId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get auto-renewal status",
            description = "Get the auto-renewal status of a subscription"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Subscription not found", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ResponseEntity<Map<String, Object>> getAutoRenewalStatus(
            @Parameter(description = "Subscription ID", required = true, example = "1")
            @PathVariable Long subscriptionId
    );


}