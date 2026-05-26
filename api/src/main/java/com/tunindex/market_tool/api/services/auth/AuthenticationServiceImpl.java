package com.tunindex.market_tool.api.services.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tunindex.market_tool.api.config.security.config.IpUaExtractor;
import com.tunindex.market_tool.api.config.security.oauth2.OAuth2TokenService;
import com.tunindex.market_tool.common.dto.auth.AuthCheckResponse;
import com.tunindex.market_tool.common.dto.auth.AuthenticationResponse;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.enums.TokenType;
import com.tunindex.market_tool.common.exception.EntityNotFoundException;
import com.tunindex.market_tool.common.exception.ErrorCodes;
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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

    @Override
    public AuthenticationResponse authenticateWithOAuth2(OAuth2AuthenticationToken oauthToken, HttpServletRequest request) {
        log.info("🔐 OAuth2 authentication attempt");

        // Extract user info from Google OAuth2 token
        Map<String, Object> attributes = oauthToken.getPrincipal().getAttributes();
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String providerId = (String) attributes.get("sub");

        log.info("OAuth2 user: email={}, provider={}", email, provider);

        // Find or create user
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.findUserByEmail(email)
                        .map(existingUser -> {
                            // Link OAuth2 provider to existing user
                            existingUser.setProvider(provider);
                            existingUser.setProviderId(providerId);
                            return userRepository.save(existingUser);
                        })
                        .orElseGet(() -> {
                            // Create new user
                            User newUser = new User();
                            newUser.setEmail(email);
                            newUser.setFirstName(name != null ? name.split(" ")[0] : "");
                            newUser.setLastName(name != null && name.split(" ").length > 1 ? name.split(" ")[1] : "");
                            newUser.setProvider(provider);
                            newUser.setProviderId(providerId);
                            newUser.setLocked(false);
                            newUser.setNumTel("0000000000");
                            return userRepository.save(newUser);
                        })
                );

        // Generate opaque tokens
        String accessToken = oauth2TokenService.generateAccessToken();
        String refreshToken = oauth2TokenService.generateRefreshToken();

        // Get IP and User-Agent hashes
        String ipHash = ipUaExtractor.hashIp(request);
        String uaHash = ipUaExtractor.hashUserAgent(request);

        // Revoke old tokens and save new ones
        revokeAllUserTokens(user);

        // Store access token (15 minutes expiry)
        oauth2TokenService.storeToken(accessToken, TokenType.OAUTH2_ACCESS,
                user.getId(), user.getEmail(), request, 15);

        // Store refresh token (7 days expiry)
        oauth2TokenService.storeToken(refreshToken, TokenType.OAUTH2_REFRESH,
                user.getId(), user.getEmail(), request, 10080);

        // Set cookies
        setAuthCookies(request, accessToken, refreshToken);

        log.info("✅ OAuth2 authentication successful for user: {}", user.getEmail());

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public AuthenticationResponse authenticateWithToken(String token, HttpServletRequest request) {
        log.info("🔐 Token authentication attempt");

        var tokenOpt = oauth2TokenService.validateToken(token, request);

        if (tokenOpt.isEmpty()) {
            throw new RuntimeException("Invalid or expired token");
        }

        var unifiedToken = tokenOpt.get();
        String email = unifiedToken.getUserEmail();

        User user = userRepository.findUserByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found", ErrorCodes.USER_NOT_FOUND, List.of()));

        // Generate new tokens
        String newAccessToken = oauth2TokenService.generateAccessToken();
        String newRefreshToken = oauth2TokenService.generateRefreshToken();

        String ipHash = ipUaExtractor.hashIp(request);
        String uaHash = ipUaExtractor.hashUserAgent(request);

        revokeAllUserTokens(user);

        oauth2TokenService.storeToken(newAccessToken, TokenType.OAUTH2_ACCESS,
                user.getId(), user.getEmail(), request, 15);
        oauth2TokenService.storeToken(newRefreshToken, TokenType.OAUTH2_REFRESH,
                user.getId(), user.getEmail(), request, 10080);

        setAuthCookies(request, newAccessToken, newRefreshToken);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    private void setAuthCookies(HttpServletRequest request, String accessToken, String refreshToken) {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getResponse();

        if (response != null) {
            ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                    .httpOnly(true)
                    .secure(PRODUCTION_ENVIRONMENT)
                    .path("/")
                    .maxAge(Duration.ofMinutes(15))
                    .sameSite(PRODUCTION_ENVIRONMENT ? "Strict" : "Lax")
                    .build();

            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(PRODUCTION_ENVIRONMENT)
                    .path("/")
                    .maxAge(Duration.ofDays(7))
                    .sameSite(PRODUCTION_ENVIRONMENT ? "Strict" : "Lax")
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

            log.info("✅ OAuth2 cookies set successfully");
        }
    }

    @Transactional
    public void revokeAllUserTokens(User user) {
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
            Optional<String> newAccessTokenOpt = oauth2TokenService.refreshAccessToken(refreshToken, request);

            if (newAccessTokenOpt.isEmpty()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid refresh token");
                return;
            }

            String newAccessToken = newAccessTokenOpt.get();
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
            return AuthCheckResponse.builder()
                    .isAuthenticated(false)
                    .build();
        }

        User user = userRepository.findUserByEmail(email)
                .orElse(null);

        if (user == null) {
            return AuthCheckResponse.builder()
                    .isAuthenticated(false)
                    .build();
        }

        long validTokensCount = unifiedTokenRepository.countActiveOAuth2TokensByUser(user.getId(), java.time.LocalDateTime.now());

        return AuthCheckResponse.builder()
                .isAuthenticated(validTokensCount > 0)
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