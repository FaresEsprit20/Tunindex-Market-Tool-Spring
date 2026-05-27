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
import com.tunindex.market_tool.api.repository.UnifiedTokenRepository;
import com.tunindex.market_tool.api.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
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
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest authRequest, HttpServletRequest request) {
        log.info("🔐 Username/password authentication attempt for user: {}", authRequest.getLogin());

        // Validate credentials
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequest.getLogin(),
                        authRequest.getPassword()
                )
        );

        // Load user
        User user = userRepository.findUserByEmail(authRequest.getLogin())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (user.getLocked()) {
            throw new RuntimeException("User account is locked");
        }

        // Generate opaque tokens
        String accessToken = oauth2TokenService.generateAccessToken();
        String refreshToken = oauth2TokenService.generateRefreshToken();

        String ipHash = ipUaExtractor.hashIp(request);
        String uaHash = ipUaExtractor.hashUserAgent(request);

        revokeAllUserTokens(user);

        oauth2TokenService.storeToken(accessToken, TokenType.OAUTH2_ACCESS,
                user.getId(), user.getEmail(), request, 15);
        oauth2TokenService.storeToken(refreshToken, TokenType.OAUTH2_REFRESH,
                user.getId(), user.getEmail(), request, 10080);

        setAuthCookies(request, accessToken, refreshToken);

        log.info("✅ Username/password authentication successful for user: {}", user.getEmail());

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

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
                            if (name != null) {
                                String[] nameParts = name.split(" ", 2);
                                newUser.setFirstName(nameParts[0]);
                                if (nameParts.length > 1) {
                                    newUser.setLastName(nameParts[1]);
                                }
                            } else {
                                newUser.setFirstName(provider);
                                newUser.setLastName("User");
                            }
                            newUser.setProvider(provider);
                            newUser.setProviderId(providerId);
                            newUser.setLocked(false);
                            newUser.setNumTel("0000000000");
                            newUser.setPassword(passwordEncoder.encode("OAUTH2_USER_" + System.currentTimeMillis()));
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

    @Override
    public AuthenticationResponse refreshAccessToken(String refreshToken, HttpServletRequest request) {
        log.info("🔐 Refresh token authentication attempt");

        Optional<String> newAccessTokenOpt = oauth2TokenService.refreshAccessToken(refreshToken, request);

        if (newAccessTokenOpt.isEmpty()) {
            throw new RuntimeException("Invalid refresh token");
        }

        String newAccessToken = newAccessTokenOpt.get();

        // Get user info from old refresh token
        var tokenOpt = oauth2TokenService.validateToken(refreshToken, request);
        String email = tokenOpt.map(t -> t.getUserEmail()).orElse(null);

        User user = null;
        if (email != null) {
            user = userRepository.findUserByEmail(email).orElse(null);
        }

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public AuthenticationResponse exchangeGoogleCode(String authorizationCode, HttpServletRequest request) {
        log.info("Exchanging Google authorization code for tokens");

        try {
            RestTemplate restTemplate = new RestTemplate();

            // Exchange code for tokens with Google
            String tokenUrl = "https://oauth2.googleapis.com/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(googleClientId, googleClientSecret);
            headers.set("Content-Type", "application/x-www-form-urlencoded");

            String body = "code=" + authorizationCode +
                    "&redirect_uri=" + googleRedirectUri +
                    "&grant_type=authorization_code";

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(tokenUrl, HttpMethod.POST, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to exchange Google code: " + response.getStatusCode());
            }

            Map<String, Object> tokenResponse = response.getBody();
            String googleAccessToken = (String) tokenResponse.get("access_token");

            // Get user info from Google
            String userInfoUrl = "https://www.googleapis.com/oauth2/v3/userinfo";
            HttpHeaders userInfoHeaders = new HttpHeaders();
            userInfoHeaders.setBearerAuth(googleAccessToken);
            HttpEntity<?> userInfoEntity = new HttpEntity<>(userInfoHeaders);

            ResponseEntity<Map> userInfoResponse = restTemplate.exchange(userInfoUrl, HttpMethod.GET, userInfoEntity, Map.class);
            Map<String, Object> userInfo = userInfoResponse.getBody();

            String email = (String) userInfo.get("email");
            String name = (String) userInfo.get("name");
            String providerId = (String) userInfo.get("sub");

            log.info("Google user info - email: {}, name: {}", email, name);

            // Find or create user
            User user = userRepository.findByProviderAndProviderId("google", providerId)
                    .orElseGet(() -> userRepository.findUserByEmail(email)
                            .map(existingUser -> {
                                existingUser.setProvider("google");
                                existingUser.setProviderId(providerId);
                                return userRepository.save(existingUser);
                            })
                            .orElseGet(() -> {
                                User newUser = new User();
                                newUser.setEmail(email);
                                if (name != null) {
                                    String[] nameParts = name.split(" ", 2);
                                    newUser.setFirstName(nameParts[0]);
                                    if (nameParts.length > 1) {
                                        newUser.setLastName(nameParts[1]);
                                    }
                                } else {
                                    newUser.setFirstName("Google");
                                    newUser.setLastName("User");
                                }
                                newUser.setProvider("google");
                                newUser.setProviderId(providerId);
                                newUser.setLocked(false);
                                newUser.setNumTel("0000000000");
                                newUser.setPassword(passwordEncoder.encode("OAUTH2_USER_" + System.currentTimeMillis()));
                                return userRepository.save(newUser);
                            })
                    );

            // Generate your opaque tokens
            String accessToken = oauth2TokenService.generateAccessToken();
            String refreshToken = oauth2TokenService.generateRefreshToken();

            String ipHash = ipUaExtractor.hashIp(request);
            String uaHash = ipUaExtractor.hashUserAgent(request);

            revokeAllUserTokens(user);

            oauth2TokenService.storeToken(accessToken, TokenType.OAUTH2_ACCESS,
                    user.getId(), user.getEmail(), request, 15);
            oauth2TokenService.storeToken(refreshToken, TokenType.OAUTH2_REFRESH,
                    user.getId(), user.getEmail(), request, 10080);

            setAuthCookies(request, accessToken, refreshToken);

            log.info("✅ Google code exchange successful for user: {}", user.getEmail());

            return AuthenticationResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (Exception e) {
            log.error("Google code exchange failed: {}", e.getMessage());
            throw new RuntimeException("Google authentication failed: " + e.getMessage());
        }
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