package com.tunindex.market_tool.api.controllers.auth;

import com.tunindex.market_tool.api.dto.auth.ChangePasswordUserRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Authentication", description = "API for user authentication")
public interface AuthenticationApi {

    @Operation(summary = "Authenticate user with optional 2FA",
            description = "Authenticates user credentials and returns either JWT tokens or 2FA challenge")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful or 2FA required"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid input"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Account locked or disabled")
    })
    @PostMapping(AUTHENTICATION_ENDPOINT + "/authenticate")
    ResponseEntity<AuthenticationTfoDto> authenticate(
            @Valid @RequestBody AuthenticationRequestMfoDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse response);

    @Operation(summary = "Verify 2FA code",
            description = "Verifies the two-factor authentication code")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "2FA verification successful"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid 2FA code"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid input"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Expired verification token")
    })
    @PostMapping(AUTHENTICATION_ENDPOINT + "/verify2fa")
    ResponseEntity<TwoFactorVerificationResponse> verifyTwoFactorCode(
            @Valid @RequestBody TwoFactorVerificationRequest verificationRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse response);


    @Operation(summary = "Resend OTP for 2FA",
            description = "Resends a new OTP code for two-factor authentication")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP resent successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid email"),
            @ApiResponse(responseCode = "404", description = "Not found - Email not registered"),
            @ApiResponse(responseCode = "429", description = "Too many requests - Rate limit exceeded")
    })
    @PostMapping(AUTHENTICATION_ENDPOINT + "/resendotp")
    ResponseEntity<AuthenticationTfoDto> resendOtp(
            @RequestParam String email,
            HttpServletRequest request,
            HttpServletResponse response);

    @Operation(summary = "Check authentication state by user email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication state returned"),
            @ApiResponse(responseCode = "403", description = "No Authentication was Found")

    })
    @GetMapping(AUTHENTICATION_ENDPOINT + "/check-auth")
    ResponseEntity<AuthCheckResponse> checkAuthentication(Authentication authentication);

    
    @Operation(summary = "Request password reset link",
            description = "Sends a password reset link to the user's email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset link sent"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid email address"),
            @ApiResponse(responseCode = "404", description = "Not found - Email not registered")
    })
    @PostMapping(value = AUTHENTICATION_ENDPOINT + "/password-reset", produces = "application/json")
    ResponseEntity<PasswordResetResponse> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto,
            HttpServletRequest request);

    @Operation(summary = "Resend password reset link",
            description = "Resends the password reset link to the user's email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset link resent"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid email address"),
            @ApiResponse(responseCode = "404", description = "Not found - Email not registered"),
            @ApiResponse(responseCode = "429", description = "Too many requests - Rate limit exceeded")
    })
    @PostMapping(path = AUTHENTICATION_ENDPOINT + "/password-reset/resend", produces = "application/json")
    ResponseEntity<PasswordResetResponse> resendResetLink(
            @Valid @RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto,
            HttpServletRequest request);

    @Operation(summary = "Reset password using token",
            description = "Resets the user's password using the provided token and new password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - Invalid token or password"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Expired or used token")
    })
    @PostMapping(path = AUTHENTICATION_ENDPOINT + "/password-reset/confirm", produces = "application/json")
    ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ChangePasswordUserRequestDto newPassword);

    @Operation(summary = "Verify reset token",
            description = "Checks if the reset token is valid, expired, or already used")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "400", description = "Bad request - Missing token"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Token is invalid, expired, or already used")
    })
    @GetMapping(path = AUTHENTICATION_ENDPOINT + "/reset-token-verify", produces = "application/json")
    ResponseEntity<TokenVerificationResponse> verifyResetToken(
            @RequestParam("token") String token);

    ResponseEntity<Void> refreshToken(HttpServletRequest request, HttpServletResponse response);


}