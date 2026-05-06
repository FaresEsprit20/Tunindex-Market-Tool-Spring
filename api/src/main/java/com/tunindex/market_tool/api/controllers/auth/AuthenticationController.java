package com.tunindex.market_tool.api.controllers.auth;

import com.fares.stock.management.core.config.security.jwt.JwtService;
import com.fares.stock.management.core.exception.ErrorCodes;
import com.fares.stock.management.core.exception.InvalidEntityException;
import com.fares.stock.management.core.exception.InvalidOperationException;
import com.fares.stock.management.domain.controllers.api.AuthenticationApi;
import com.fares.stock.management.domain.dto.auth.AuthCheckResponse;
import com.fares.stock.management.domain.dto.auth.ChangePasswordUserRequestDto;
import com.fares.stock.management.domain.dto.password_reset.*;
import com.fares.stock.management.domain.dto.two_factor.*;
import com.fares.stock.management.domain.services.AuthenticationService;
import com.fares.stock.management.domain.services.password_reset.PasswordResetService;
import com.fares.stock.management.domain.services.two_facor.TwoFactorAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static com.fares.stock.management.core.utils.constants.Constants.PRODUCTION_ENVIRONMENT;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthenticationController implements AuthenticationApi {

    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;
    private final TwoFactorAuthService twoFactorAuthService;
    private final JwtService jwtService;

    private static final String[] FAKE_COOKIE_NAMES = {
            "auth_token", "session_key", "user_token", "access_key", "secure_session"
    };

    @Override
    public ResponseEntity<AuthenticationTfoDto> authenticate(
            @Valid @RequestBody AuthenticationRequestMfoDto request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {

        log.info("Authentication attempt for user: {}", request.getLogin());

        // Clean all previous cookies BEFORE authentication
        deleteAllCookies(httpRequest, response);

        authenticationService.authenticate(request, httpRequest);
        String verificationToken = twoFactorAuthService.generateAndSendOtp(request.getLogin());
        Map<String, Long> timingInfo = twoFactorAuthService.getTimingInfo(request.getLogin());


        return ResponseEntity.ok(AuthenticationTfoDto.builder()
                .twoFactorDeliveryMethod("EMAIL")
                .twoFactorVerificationToken(verificationToken)
                .otpValidForSeconds(timingInfo.get("otpValidForSeconds"))
                .resendAllowedInSeconds(timingInfo.get("resendAllowedInSeconds"))
                .build());
    }

    @Override
    public ResponseEntity<TwoFactorVerificationResponse> verifyTwoFactorCode(
            @Valid @RequestBody TwoFactorVerificationRequest verificationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("2FA verification attempt for token:  ******************** ");

        try {
            boolean isVerified = twoFactorAuthService.verifyOtp(
                    verificationRequest.getVerificationToken(),
                    verificationRequest.getCode());

            if (!isVerified) {
                log.warn("2FA verification failed");
                List<String> errors = new ArrayList<>();
                errors.add("2FA verification failed");
                throw new InvalidEntityException("Invalid 2FA code", ErrorCodes.TWO_FACTOR_TOKEN_INVALID,
                        errors);
            }

            String userEmail = twoFactorAuthService.getUserEmailByVerificationToken(
                    verificationRequest.getVerificationToken());

            AuthenticationTfoDto authResponse = authenticationService.generatePostMfaTokens(
                    userEmail,
                    request);

            log.info("🔑 Generated tokens for user: {}", userEmail);
            log.info("🔑 AccessToken present: {}", authResponse.getAccessToken() != null);
            log.info("🔑 RefreshToken present: {}", authResponse.getRefreshToken() != null);
            
            // Set cookies ONLY after successful 2FA verification
            setAuthCookies(request, response, authResponse);
            setDecoyCookies(response);

            log.info("✅ 2FA verification successful for user: {}", userEmail);
            log.info("🍪 Cookies should now be set in response");
            return ResponseEntity.ok(
                    TwoFactorVerificationResponse.builder()
                            .success(true)
                            .message("Authentication complete")
                            .build());

        } catch (SecurityException e) {
            log.error("IP validation failed: {}", e.getMessage());
            List<String> errors = new ArrayList<>();
            errors.add("2FA verification failed, Network Restriction ");
            throw new InvalidOperationException("Invalid Multi Factor Operation, Network / IP Error, ", ErrorCodes.TWO_FACTOR_ACCOUNT_BLOCKED,
                    errors);
        }
    }

    @Override
    public ResponseEntity<AuthenticationTfoDto> resendOtp(
            @RequestParam String email,
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("Resending OTP for user: {}", email);

        String verificationToken = twoFactorAuthService.resendOtp(email);
        Map<String, Long> timingInfo = twoFactorAuthService.getTimingInfo(email);

        setDecoyCookies(response);

        return ResponseEntity.ok(AuthenticationTfoDto.builder()
                .twoFactorDeliveryMethod("EMAIL")
                .twoFactorVerificationToken(verificationToken)
                .otpValidForSeconds(timingInfo.get("otpValidForSeconds"))
                .resendAllowedInSeconds(timingInfo.get("resendAllowedInSeconds"))
                .build());
    }

    @Override
    public ResponseEntity<AuthCheckResponse> checkAuthentication(Authentication authentication) {
        log.info("🔍 /check-auth called");
        log.info("🔍 Authentication object: {}", authentication);
        log.info("🔍 Is authenticated: {}", authentication != null && authentication.isAuthenticated());
        
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("❌ User NOT authenticated - returning false");
            return ResponseEntity.ok(new AuthCheckResponse(false, null, null));
        }

        String email = authentication.getName();
        log.info("✅ User IS authenticated: {}", email);
        AuthCheckResponse response = authenticationService.checkUserAuthentication(email);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PasswordResetResponse> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto,
            HttpServletRequest request) {

        log.info("Password reset request for email: {}", forgotPasswordRequestDto.getEmail());

        passwordResetService.sendResetLink(
                forgotPasswordRequestDto.getEmail(),
                null,
                request.getRemoteAddr(),
                null);

        return ResponseEntity.ok(
                PasswordResetResponse.builder()
                        .message("Password reset link has been sent to your email")
                        .remainingTimeSeconds(180)
                        .build());
    }

    @Override
    public ResponseEntity<PasswordResetResponse> resendResetLink(
            @Valid @RequestBody ForgotPasswordRequestDto forgotPasswordRequestDto,
            HttpServletRequest request) {

        log.info("Resending password reset link for email: {}", forgotPasswordRequestDto.getEmail());

        passwordResetService.resendResetLink(
                forgotPasswordRequestDto.getEmail(),
                null,
                request.getRemoteAddr(),
                null);

        return ResponseEntity.ok(
                PasswordResetResponse.builder()
                        .message("Password reset link has been sent to your email")
                        .remainingTimeSeconds(180)
                        .build());
    }

    @Override
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ChangePasswordUserRequestDto newPassword) {

        log.info("Password reset attempt with token");

        boolean isReset = passwordResetService.resetPassword(newPassword.getToken(), newPassword);
        return isReset
                ? ResponseEntity.ok(new MessageResponse("Password has been reset successfully"))
                : ResponseEntity.badRequest().body(new MessageResponse("Failed to reset password"));
    }

    @Override
    public ResponseEntity<TokenVerificationResponse> verifyResetToken(@RequestParam("token") String token) {
        log.info("Verifying reset token");
        TokenVerificationResponse response = passwordResetService.verifyToken(token);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        try {
            authenticationService.refreshToken(request, response);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }

    private void setAuthCookies(HttpServletRequest request, HttpServletResponse response, AuthenticationTfoDto authResponse) {
        log.info("🍪 Setting auth cookies AFTER successful 2FA verification...");

        if (authResponse.getAccessToken() != null) {
            ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("accessToken", authResponse.getAccessToken())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(Duration.ofHours(24));
            
            if (!PRODUCTION_ENVIRONMENT) {
                // Development: Allow cross-port cookies (4200 → 8082)
                cookieBuilder.secure(false);
                cookieBuilder.sameSite("Lax");
                log.info("🔧 DEV MODE: Cookie with secure=false, sameSite=Lax");
            } else {
                // Production: Maximum security
                cookieBuilder.secure(true);
                cookieBuilder.sameSite("Strict");
            }
            
            response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
            log.info("✅ AccessToken cookie set");
        } else {
            log.error("❌ NO ACCESS TOKEN in authResponse!");
        }

        if (authResponse.getRefreshToken() != null) {
            ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie.from("refreshToken", authResponse.getRefreshToken())
                    .httpOnly(true)
                    .path("/")
                    .maxAge(Duration.ofDays(7));
            
            if (!PRODUCTION_ENVIRONMENT) {
                // Development: Allow cross-port cookies (4200 → 8082)
                cookieBuilder.secure(false);
                cookieBuilder.sameSite("Lax");
            } else {
                // Production: Maximum security
                cookieBuilder.secure(true);
                cookieBuilder.sameSite("Strict");
            }
            
            response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
            log.info("✅ RefreshToken cookie set");
        } else {
            log.error("❌ NO REFRESH TOKEN in authResponse!");
        }
        
        log.info("🍪 Finished setting cookies. Total Set-Cookie headers added: 2");
    }

    private void setDecoyCookies(HttpServletResponse response) {
        int numFakeCookies = 2 + ThreadLocalRandom.current().nextInt(3);
        for (int i = 0; i < numFakeCookies; i++) {
            String cookieName = FAKE_COOKIE_NAMES[ThreadLocalRandom.current().nextInt(FAKE_COOKIE_NAMES.length)];
            String cookieValue = generateFakeCookieValue(cookieName);

            ResponseCookie fakeCookie = ResponseCookie.from(cookieName, cookieValue)
                    .httpOnly(ThreadLocalRandom.current().nextBoolean())
                    .secure(PRODUCTION_ENVIRONMENT)
                    .path(ThreadLocalRandom.current().nextBoolean() ? "/api" : "/")
                    .maxAge(Duration.ofDays(ThreadLocalRandom.current().nextInt(1, 30)))
                    .sameSite(ThreadLocalRandom.current().nextBoolean() ? "Lax" : "Strict")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, fakeCookie.toString());
        }

        ResponseCookie baitCookie = ResponseCookie.from("admin_token", jwtService.generateFakeAccessToken())
                .httpOnly(false)
                .secure(true)
                .path("/admin")
                .maxAge(Duration.ofHours(1))
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, baitCookie.toString());
    }

    private String generateFakeCookieValue(String name) {
        if (name.contains("token")) {
            return jwtService.generateFakeAccessToken();
        }
        return "decoy_" + UUID.randomUUID() + "_" + System.currentTimeMillis();
    }

    private void deleteAllCookies(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                ResponseCookie expiredCookie = ResponseCookie.from(cookie.getName(), "")
                        .path("/")
                        .maxAge(0)
                        .httpOnly(cookie.isHttpOnly())
                        .secure(cookie.getSecure())
                        .sameSite(PRODUCTION_ENVIRONMENT ? "Strict" : "Lax")  // Lax for dev, Strict for prod
                        .build();
                response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
            }
        }
    }


}
