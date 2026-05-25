package com.tunindex.market_tool.api.services.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.api.config.security.config.IpUaExtractor;
import com.tunindex.market_tool.api.config.security.oauth2.OAuth2TokenService;
import com.tunindex.market_tool.common.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.common.dto.auth.AuthenticationRequest;
import com.tunindex.market_tool.common.dto.auth.AuthenticationResponse;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.enums.TokenType;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.api.repository.UnifiedTokenRepository;
import com.tunindex.market_tool.api.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static com.tunindex.market_tool.common.utils.constants.Constants.PRODUCTION_ENVIRONMENT;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final UnifiedTokenRepository unifiedTokenRepository;
    private final OAuth2TokenService oauth2TokenService;
    private final IpUaExtractor ipUaExtractor;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest authRequest, HttpServletRequest request) {
        log.info("🔐 Authentication attempt for user: {}", authRequest.getLogin());

        // 1. Validate credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequest.getLogin(),
                        authRequest.getPassword()
                )
        );

        // 2. Load and validate user
        User user = userRepository.findUserByEmail(authRequest.getLogin())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.getLocked()) {
            throw new InvalidOperationException(
                    "User is locked",
                    ErrorCodes.USER_ACCOUNT_LOCK_NOT_VALID,
                    List.of("User account is locked")
            );
        }

        // 3. Generate opaque tokens (not JWT)
        String accessToken = oauth2TokenService.generateAccessToken();
        String refreshToken = oauth2TokenService.generateRefreshToken();

        // 4. Get IP and User-Agent hashes
        String ipHash = ipUaExtractor.hashIp(request);
        String uaHash = ipUaExtractor.hashUserAgent(request);

        // 5. Revoke old tokens and save new ones
        revokeAllUserTokens(user);

        // Store access token (15 minutes expiry)
        oauth2TokenService.storeToken(accessToken, TokenType.OAUTH2_ACCESS,
                user.getId(), user.getEmail(), request, 15);

        // Store refresh token (7 days expiry)
        oauth2TokenService.storeToken(refreshToken, TokenType.OAUTH2_REFRESH,
                user.getId(), user.getEmail(), request, 10080); // 7 days = 10080 minutes

        // 6. Set cookies
        setAuthCookies(request, accessToken, refreshToken);

        log.info("✅ Authentication successful for user: {} with opaque tokens", user.getEmail());

        // Return same DTO structure (no changes to DTO)
        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private void setAuthCookies(HttpServletRequest request, String accessToken, String refreshToken) {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getResponse();

        if (response != null) {
            // Set access token cookie (15 minutes)
            ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                    .httpOnly(true)
                    .secure(PRODUCTION_ENVIRONMENT)
                    .path("/")
                    .maxAge(Duration.ofMinutes(15))
                    .sameSite(PRODUCTION_ENVIRONMENT ? "Strict" : "Lax")
                    .build();

            // Set refresh token cookie (7 days)
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(PRODUCTION_ENVIRONMENT)
                    .path("/")
                    .maxAge(Duration.ofDays(7))
                    .sameSite(PRODUCTION_ENVIRONMENT ? "Strict" : "Lax")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

            log.info("✅ OAuth2 opaque cookies set successfully");
        }
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
        // Revoke all OAuth2 tokens for this user
        unifiedTokenRepository.revokeAllOAuth2TokensByUser(user.getId());
        log.info("Revoked all tokens for user: {}", user.getEmail());
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
            // Validate refresh token and get new access token
            Optional<String> newAccessTokenOpt = oauth2TokenService.refreshAccessToken(refreshToken, request);

            if (newAccessTokenOpt.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid refresh token");
                return;
            }

            String newAccessToken = newAccessTokenOpt.get();
            setAccessTokenCookie(response, newAccessToken);

            // Return same DTO structure (no changes)
            AuthenticationResponse authResponse = AuthenticationResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .build();

            new ObjectMapper().writeValue(response.getWriter(), authResponse);

        } catch (Exception e) {
            log.error("Token refresh failed", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Token refresh failed");
        }
    }

    @Override
    public AuthCheckResponse checkUserAuthentication(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User with EMAIL " + email + " is not found",
                        ErrorCodes.USER_NOT_FOUND,
                        List.of("User with EMAIL " + email + " is not found")
                ));

        // Check for active OAuth2 tokens instead of JWT
        long validTokensCount = unifiedTokenRepository.countActiveOAuth2TokensByUser(user.getId(), java.time.LocalDateTime.now());

        boolean isAuthenticated = validTokensCount > 0;

        return AuthCheckResponse.builder()
                .isAuthenticated(isAuthenticated)
                .email(user.getEmail())
                .userId(user.getId())
                .build();
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
                .maxAge(Duration.ofMinutes(15))
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }


}