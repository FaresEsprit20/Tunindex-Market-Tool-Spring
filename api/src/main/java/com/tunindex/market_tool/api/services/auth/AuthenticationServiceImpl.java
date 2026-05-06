package com.tunindex.market_tool.api.services.auth;

import com.fares.stock.management.core.config.security.jwt.JwtService;
import com.fares.stock.management.core.exception.EntityNotFoundException;
import com.fares.stock.management.core.exception.ErrorCodes;
import com.fares.stock.management.core.exception.InvalidOperationException;
import com.fares.stock.management.core.exception.RecaptchaException;
import com.fares.stock.management.core.validators.auth.AuthenticationRequestValidator;
import com.fares.stock.management.domain.dto.auth.AuthCheckResponse;
import com.fares.stock.management.domain.dto.token.JwtTokenDto;
import com.fares.stock.management.domain.dto.two_factor.AuthenticationRequestMfoDto;
import com.fares.stock.management.domain.dto.two_factor.AuthenticationTfoDto;
import com.fares.stock.management.domain.entities.User;
import com.fares.stock.management.domain.entities.enums.TokenType;
import com.fares.stock.management.domain.entities.token.UnifiedToken;
import com.fares.stock.management.domain.repository.jpa.UserRepository;
import com.fares.stock.management.domain.repository.jpa.token.JwtTokenRepository;
import com.fares.stock.management.domain.repository.jpa.token.UnifiedTokenRepository;
import com.fares.stock.management.domain.services.AuthenticationService;
import com.fares.stock.management.domain.services.RecaptchaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.fares.stock.management.core.utils.constants.Constants.PRODUCTION_ENVIRONMENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final JwtTokenRepository tokenRepository;
    private final UnifiedTokenRepository unifiedTokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RecaptchaService recaptchaService;

    @Override
    public AuthenticationTfoDto authenticate(AuthenticationRequestMfoDto authRequest,
                                             HttpServletRequest request) {
        List<String> errors = new ArrayList<>();

        validateInitialAuth(authRequest, request, errors);
        User user = loadAndValidateUser(authRequest.getLogin(), errors);

        if (authRequest.getTwoFactorCode() == null) {
            return AuthenticationTfoDto.builder()
                    .twoFactorDeliveryMethod("EMAIL")
                    .build();
        }

        return generateTokens(user, request);
    }

    // In AuthenticationServiceImpl
    @Override
    @Transactional
    public AuthenticationTfoDto generatePostMfaTokens(String userEmail, HttpServletRequest request) {
        log.info("🔐 Generating post-MFA tokens for user: {}", userEmail);
        
        User user = userRepository.findUserByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Generate tokens
        String accessToken = jwtService.generateToken(user, request);
        String refreshToken = jwtService.generateRefreshToken(user, request);

        log.info("✅ Tokens generated successfully");
        log.info("🔑 AccessToken length: {}", accessToken != null ? accessToken.length() : 0);
        log.info("🔑 RefreshToken length: {}", refreshToken != null ? refreshToken.length() : 0);

        // Revoke old tokens
        revokeAllUserTokens(user);

        // Store new token
        saveUserToken(user, accessToken, request);

        // NOTE: Cookies are set in AuthenticationController AFTER 2FA verification
        // NOT here - this maintains security (no cookies before 2FA is verified)

        return AuthenticationTfoDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private void setAuthCookies(HttpServletRequest request, String accessToken, String refreshToken) {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getResponse();

        if (response != null) {
            log.info("🍪 Setting JWT cookies in AuthenticationServiceImpl");
            
            // Set access token cookie (short-lived)
            ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                    .httpOnly(true)
                    .secure(PRODUCTION_ENVIRONMENT)
                    .path("/")
                    .maxAge(Duration.ofMinutes(15).toSeconds())
                    .sameSite(PRODUCTION_ENVIRONMENT ? "Strict" : "Lax")  // FIX: Lax for dev
                    .build();

            // Set refresh token cookie (longer-lived)
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(PRODUCTION_ENVIRONMENT)
                    .path("/")
                    .maxAge(Duration.ofDays(7).toSeconds())
                    .sameSite(PRODUCTION_ENVIRONMENT ? "Strict" : "Lax")  // FIX: Lax for dev
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
            
            log.info("✅ JWT cookies set: accessToken (15 min), refreshToken (7 days)");
            log.info("🔧 Cookie settings: httpOnly=true, secure={}, sameSite={}", 
                PRODUCTION_ENVIRONMENT, PRODUCTION_ENVIRONMENT ? "Strict" : "Lax");
        } else {
            log.error("❌ Cannot set cookies - HttpServletResponse is null!");
        }
    }

    private AuthenticationTfoDto generateTokens(User user, HttpServletRequest request) {
        String accessToken = jwtService.generateToken(user, request);
        String refreshToken = jwtService.generateRefreshToken(user, request);

        revokeAllUserTokens(user);
        saveUserToken(user, accessToken, request);

        return AuthenticationTfoDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private void saveUserToken(User user, String jwtToken, HttpServletRequest request) {
        String ipHash = jwtService.hashIp(jwtService.getValidatedIp(request));
        String userAgentHash = jwtService.hashUserAgent(request);

        UnifiedToken token = UnifiedToken.builder()
                .user(user)
                .token(jwtToken)
                .ipHash(ipHash)
                .userAgentHash(userAgentHash)
                .tokenType(TokenType.JWT)
                .expired(false)
                .revoked(false)
                .build();
        unifiedTokenRepository.save(token);
    }

    private void validateInitialAuth(AuthenticationRequestMfoDto authRequest,
                                     HttpServletRequest request,
                                     List<String> errors) {
        AuthenticationRequestValidator.validate(authRequest);
        validateRecaptcha(authRequest, request, errors);
        authenticateCredentials(authRequest, errors);
    }

    private void validateRecaptcha(AuthenticationRequestMfoDto authRequest,
                                   HttpServletRequest request,
                                   List<String> errors) {
        try {
            String clientIp = getClientIpAddress(request);
            if (!recaptchaService.validate(authRequest.getRecaptchaToken(), clientIp, "LOGIN")) {
                errors.add("reCAPTCHA validation failed");
                throw new RecaptchaException("reCAPTCHA validation failed", ErrorCodes.RECAPTCHA_VALIDATION_FAILED);
            }
        } catch (RecaptchaException e) {
            errors.add("Security validation failed. Please try again.");
            throw e;
        }
    }

    private void authenticateCredentials(AuthenticationRequestMfoDto authRequest, List<String> errors) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authRequest.getLogin(),
                            authRequest.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            errors.add("Invalid login or password");
            throw new BadCredentialsException("Invalid credentials");
        }
    }

    private User loadAndValidateUser(String email, List<String> errors) {
        tokenRepository.deleteAllByUserEmail(email);
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getLocked()) {
            errors.add("User is locked!");
            throw new InvalidOperationException(
                    "User is locked",
                    ErrorCodes.USER_ACCOUNT_LOCK_NOT_VALID,
                    errors
            );
        }
        return user;
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void revokeAllUserTokens(User user) {
        var validTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        // Revoke all JWT tokens for the user
        unifiedTokenRepository.deleteByUserEmailAndType(user.getEmail(), TokenType.JWT);
    }

    @Override
    public void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");

        String refreshToken = extractRefreshToken(request);
        if (refreshToken == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing refresh token");
            return;
        }

        try {
            String userEmail = jwtService.extractUsername(refreshToken);
            User user = userRepository.findUserByEmail(userEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            if (!isTokenValid(refreshToken, user, request)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid refresh token");
                return;
            }

            String newAccessToken = jwtService.generateToken(user, request);
            setAccessTokenCookie(response, newAccessToken);
            sendTokenResponse(response, newAccessToken);

        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Token refresh failed");
        }
    }

    @Override
    public AuthCheckResponse checkUserAuthentication(String email) {
        // Validate input
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        // Find user or throw exception
        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User with EMAIL " + email + " is not found",
                        ErrorCodes.USER_NOT_FOUND,
                        List.of("User with EMAIL " + email + " is not found")
                ));

        // Check for valid tokens
        Integer tokensCount = tokenRepository.findCountAllValidTokenByUser(user.getId());
        boolean isAuthenticated = tokensCount != null && tokensCount > 0;

        return AuthCheckResponse.builder()
                .isAuthenticated(isAuthenticated)
                .email(user.getEmail())
                .userId(user.getId())
                .build();
    }

    public boolean isTokenValid(String token, User user, HttpServletRequest request) {
        // First validate the JWT itself
        if (!jwtService.isTokenValid(token, user, request)) {
            return false;
        }

        // Then check our database record
        String currentIpHash = jwtService.hashIp(jwtService.getValidatedIp(request));
        Optional<com.fares.stock.management.domain.entities.token.UnifiedToken> storedToken = tokenRepository.findByTokenAndIpHash(token, currentIpHash);
        if (storedToken.isEmpty()) {
            log.warn("No token found in database for IP hash");
            return false;
        }
        JwtTokenDto tokenEntity = JwtTokenDto.fromEntity(storedToken.get());
        if (tokenEntity.isExpired() || tokenEntity.isRevoked()) {
            log.warn("Token is expired or revoked in database");
            return false;
        }
        // Additional check - verify user agent
        String currentUserAgentHash = jwtService.hashUserAgent(request);
        if (!currentUserAgentHash.equals(tokenEntity.getUserAgentHash())) {
            log.warn("User agent hash mismatch");
            return false;
        }
        return true;
    }

    private String extractRefreshToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void setAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", token)
                .httpOnly(true)
                .secure(PRODUCTION_ENVIRONMENT)
                .path("/")
                .maxAge(Duration.ofHours(1))
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void sendTokenResponse(HttpServletResponse response, String token) throws IOException {
        new ObjectMapper().writeValue(response.getWriter(), Map.of("accessToken", token));
    }


}