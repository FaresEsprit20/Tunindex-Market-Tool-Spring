package com.tunindex.market_tool.common.services.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.common.config.security.jwt.JwtService;
import com.tunindex.market_tool.common.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.common.dto.auth.AuthenticationRequest;
import com.tunindex.market_tool.common.dto.auth.AuthenticationResponse;
import com.tunindex.market_tool.common.entities.UnifiedToken;
import com.tunindex.market_tool.common.entities.User;
import com.tunindex.market_tool.common.entities.enums.TokenType;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
import com.tunindex.market_tool.common.exception.InvalidOperationException;
import com.tunindex.market_tool.common.repository.UnifiedTokenRepository;
import com.tunindex.market_tool.common.repository.UserRepository;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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
    private final JwtService jwtService;
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

        // 3. Generate tokens
        String accessToken = jwtService.generateToken(user, request);
        String refreshToken = jwtService.generateRefreshToken(user, request);

        // 4. Revoke old tokens and save new ones
        revokeAllUserTokens(user);
        saveUserToken(user, accessToken, request);

        // 5. Set cookies
        setAuthCookies(request, accessToken, refreshToken);

        log.info("✅ Authentication successful for user: {}", user.getEmail());

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

            log.info("✅ JWT cookies set successfully");
        }
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

    @Transactional
    public void revokeAllUserTokens(User user) {
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

        long validTokensCount = unifiedTokenRepository.countByUserEmailAndTokenTypeAndExpiredFalseAndRevokedFalse(
                user.getEmail(), TokenType.JWT);

        boolean isAuthenticated = validTokensCount > 0;

        return AuthCheckResponse.builder()
                .isAuthenticated(isAuthenticated)
                .email(user.getEmail())
                .userId(user.getId())
                .build();
    }

    public boolean isTokenValid(String token, User user, HttpServletRequest request) {
        if (!jwtService.isTokenValid(token, user, request)) {
            return false;
        }

        String currentIpHash = jwtService.hashIp(jwtService.getValidatedIp(request));
        Optional<UnifiedToken> storedToken = unifiedTokenRepository.findByTokenAndIpHashAndTokenType(
                token, currentIpHash, TokenType.JWT);

        return storedToken.filter(unifiedToken -> !unifiedToken.isExpired() && !unifiedToken.isRevoked()).isPresent();

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