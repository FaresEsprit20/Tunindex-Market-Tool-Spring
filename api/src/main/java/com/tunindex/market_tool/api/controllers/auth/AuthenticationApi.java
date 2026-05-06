package com.tunindex.market_tool.api.controllers.auth;

import com.tunindex.market_tool.api.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.api.dto.auth.AuthenticationRequest;
import com.tunindex.market_tool.api.dto.auth.AuthenticationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import static com.tunindex.market_tool.api.utils.constants.Constants.APP_ROOT;

@Tag(name = "Authentication", description = "API for user authentication")
public interface AuthenticationApi {

    String AUTHENTICATION_ENDPOINT = APP_ROOT + "/auth";

    @Operation(summary = "Authenticate user",
            description = "Authenticates user credentials and returns JWT tokens")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid input"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Account locked or disabled")
    })
    @PostMapping(AUTHENTICATION_ENDPOINT + "/authenticate")
    ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response);

    @Operation(summary = "Check authentication state",
            description = "Checks if the current user is authenticated")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication state returned")
    })
    @GetMapping(AUTHENTICATION_ENDPOINT + "/check-auth")
    ResponseEntity<AuthCheckResponse> checkAuthentication(Authentication authentication);

    @Operation(summary = "Refresh token",
            description = "Refreshes the access token using refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid refresh token")
    })
    @PostMapping(AUTHENTICATION_ENDPOINT + "/refresh-token")
    ResponseEntity<Void> refreshToken(HttpServletRequest request, HttpServletResponse response);
}