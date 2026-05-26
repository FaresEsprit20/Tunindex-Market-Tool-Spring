package com.tunindex.market_tool.api.config.security.oauth2;

import com.tunindex.market_tool.api.entities.UnifiedToken;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.entities.enums.TokenType;
import com.tunindex.market_tool.api.repository.UnifiedTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenService {

    private final UnifiedTokenRepository tokenRepository;

    @Value("${application.security.ip-salt}")
    private String ipSalt;

    @Value("${application.security.trusted-proxies:}")
    private List<String> trustedProxies;

    // ============================================================
    // TOKEN GENERATION
    // ============================================================

    /**
     * Generate a new opaque access token
     */
    public String generateAccessToken() {
        return "at_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate a new opaque refresh token
     */
    public String generateRefreshToken() {
        return "rt_" + UUID.randomUUID().toString().replace("-", "");
    }

    // ============================================================
    // TOKEN STORAGE & BINDING
    // ============================================================

    /**
     * Store OAuth2 token with IP and User-Agent binding
     */
    public UnifiedToken storeToken(String token, TokenType tokenType, Integer userId, String userEmail,
                                   HttpServletRequest request, int expirationMinutes) {

        String ipHash = hashIp(getValidatedIp(request));
        String uaHash = hashUserAgent(request);

        UnifiedToken unifiedToken = UnifiedToken.builder()
                .token(token)
                .tokenType(tokenType)
                .userEmail(userEmail)
                .ipHash(ipHash)
                .userAgentHash(uaHash)
                .expirationDate(LocalDateTime.now().plusMinutes(expirationMinutes))
                .revoked(false)
                .expired(false)
                .isUsed(false)
                .build();

        // Set user if userId provided
        if (userId != null) {
            User user = new User();
            user.setId(userId);
            unifiedToken.setUser(user);
        }

        return tokenRepository.save(unifiedToken);
    }

    /**
     * Validate OAuth2 token and return it if valid
     */
    public Optional<UnifiedToken> validateToken(String token, HttpServletRequest request) {
        Optional<UnifiedToken> tokenOpt = tokenRepository.findOAuth2TokenByToken(token);

        if (tokenOpt.isEmpty()) {
            log.warn("Token not found: {}", token);
            return Optional.empty();
        }

        UnifiedToken unifiedToken = tokenOpt.get();

        // Check if token is revoked or expired
        if (unifiedToken.isRevoked() || unifiedToken.isExpired()) {
            log.warn("Token is revoked or expired: {}", token);
            return Optional.empty();
        }

        // Check expiration
        if (unifiedToken.getExpirationDate().isBefore(LocalDateTime.now())) {
            log.warn("Token expired: {}", token);
            return Optional.empty();
        }

        // Validate IP binding
        String currentIpHash = hashIp(getValidatedIp(request));
        if (!unifiedToken.getIpHash().equals(currentIpHash)) {
            log.warn("IP mismatch for token: {} (stored: {}, current: {})",
                    token, unifiedToken.getIpHash(), currentIpHash);
            return Optional.empty();
        }

        // Validate User-Agent binding
        String currentUaHash = hashUserAgent(request);
        if (!unifiedToken.getUserAgentHash().equals(currentUaHash)) {
            log.warn("User-Agent mismatch for token: {}", token);
            return Optional.empty();
        }

        return tokenOpt;
    }

    /**
     * Refresh access token using refresh token
     */
    public Optional<String> refreshAccessToken(String refreshToken, HttpServletRequest request) {
        Optional<UnifiedToken> refreshTokenOpt = tokenRepository.findOAuth2TokenByToken(refreshToken);

        if (refreshTokenOpt.isEmpty()) {
            log.warn("Refresh token not found: {}", refreshToken);
            return Optional.empty();
        }

        UnifiedToken refreshTokenEntity = refreshTokenOpt.get();

        // Check if refresh token is valid
        if (refreshTokenEntity.isRevoked() || refreshTokenEntity.isExpired() ||
                refreshTokenEntity.getExpirationDate().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token invalid or expired");
            return Optional.empty();
        }

        // Validate IP binding
        String currentIpHash = hashIp(getValidatedIp(request));
        if (!refreshTokenEntity.getIpHash().equals(currentIpHash)) {
            log.warn("IP mismatch for refresh token");
            return Optional.empty();
        }

        // Generate new access token
        String newAccessToken = generateAccessToken();

        // Store new access token
        storeToken(newAccessToken, TokenType.OAUTH2_ACCESS,
                refreshTokenEntity.getUser() != null ? refreshTokenEntity.getUser().getId() : null,
                refreshTokenEntity.getUserEmail(), request, 15);

        return Optional.of(newAccessToken);
    }

    /**
     * Revoke a token (logout)
     */
    public boolean revokeToken(String token) {
        Optional<UnifiedToken> tokenOpt = tokenRepository.findOAuth2TokenByToken(token);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        UnifiedToken unifiedToken = tokenOpt.get();
        unifiedToken.setRevoked(true);
        unifiedToken.setExpired(true);
        tokenRepository.save(unifiedToken);

        log.info("Token revoked: {}", token);
        return true;
    }

    /**
     * Revoke all tokens for a user (logout from all devices)
     */
    public void revokeAllUserTokens(Integer userId) {
        tokenRepository.revokeAllOAuth2TokensByUser(userId);
        log.info("All tokens revoked for user: {}", userId);
    }

    // ============================================================
    // IP & USER-AGENT METHODS (Moved from JwtService)
    // ============================================================

    public String hashIp(String ipAddress) {
        return DigestUtils.sha256Hex(ipAddress + ipSalt);
    }

    public String hashUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return DigestUtils.sha256Hex((userAgent != null ? userAgent : "unknown") + ipSalt);
    }

    public String getValidatedIp(HttpServletRequest request) {
        String ipChain = request.getHeader("X-Forwarded-For");
        String clientIp = null;

        if (ipChain != null && !ipChain.isEmpty()) {
            String[] ips = ipChain.split(",");
            for (int i = ips.length - 1; i >= 0; i--) {
                String ip = ips[i].trim();
                if (!isTrustedProxy(ip)) {
                    clientIp = ip;
                    break;
                }
            }
        }

        if (clientIp == null) clientIp = request.getRemoteAddr();
        validateIpFormat(clientIp);
        return clientIp;
    }

    private boolean isTrustedProxy(String ip) {
        return trustedProxies.contains(ip) || trustedProxies.stream().anyMatch(proxy -> ip.startsWith(proxy + "."));
    }

    private void validateIpFormat(String ip) {
        if (ip == null || ip.isEmpty()) {
            throw new SecurityException("IP address cannot be null or empty");
        }
        boolean isValidIpv4 = ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$");
        boolean isLocalhost = ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1");
        boolean isIpv6 = ip.contains(":");
        if (!isValidIpv4 && !isLocalhost && !isIpv6) {
            throw new SecurityException("Invalid IP address format: " + ip);
        }
    }

    // ============================================================
    // FAKE TOKEN GENERATOR (Decoy - Keep for security)
    // ============================================================

    private static final String FAKE_PREFIX = "decoy_";
    private static final List<String> FAKE_SUBJECTS = Arrays.asList("anonymous", "guest", "system", "admin", "user");
    private static final List<String> FAKE_ROLES = Arrays.asList("FAKE_ADMIN", "FAKE_USER", "FAKE_READONLY");

    public String generateFakeAccessToken() {
        String fakeIp = ThreadLocalRandom.current().nextInt(256) + "." +
                ThreadLocalRandom.current().nextInt(256) + "." +
                ThreadLocalRandom.current().nextInt(256) + "." +
                ThreadLocalRandom.current().nextInt(256);

        return FAKE_PREFIX + UUID.randomUUID().toString().replace("-", "") + "_" +
                hashIp(fakeIp).substring(0, 16);
    }

    public boolean isFakeToken(String token) {
        if (token == null) return false;
        return token.startsWith(FAKE_PREFIX);
    }
}