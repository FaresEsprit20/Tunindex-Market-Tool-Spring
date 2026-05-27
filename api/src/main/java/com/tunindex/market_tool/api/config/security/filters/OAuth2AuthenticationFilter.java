package com.tunindex.market_tool.api.config.security.filters;

import com.tunindex.market_tool.api.config.security.oauth2.OAuth2TokenService;
import com.tunindex.market_tool.api.entities.UnifiedToken;
import com.tunindex.market_tool.api.entities.User;
import com.tunindex.market_tool.api.repository.UnifiedTokenRepository;
import com.tunindex.market_tool.api.services.users.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class OAuth2AuthenticationFilter extends OncePerRequestFilter {

    private final OAuth2TokenService tokenService;
    private final CustomUserDetailsService userDetailsService;
    private final UnifiedTokenRepository tokenRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();

        // Skip filter for public endpoints
        if (path.startsWith("/oauth2/") || path.startsWith("/login/") ||
                path.startsWith("/tunindex/market/tool/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("🔍 OAuth2Filter processing: {}", path);

        // Extract token from Cookie OR Authorization header
        String token = extractToken(request);

        if (token == null) {
            log.debug("⏭️ No token found, continuing filter chain");
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("✅ Token found, validating...");

        // Validate token with OAuth2TokenService
        Optional<UnifiedToken> tokenOpt = tokenService.validateToken(token, request);

        if (tokenOpt.isEmpty()) {
            log.warn("❌ Invalid or expired token");
            filterChain.doFilter(request, response);
            return;
        }

        UnifiedToken unifiedToken = tokenOpt.get();
        String userEmail = unifiedToken.getUserEmail();

        log.debug("📧 Extracted email from token: {}", userEmail);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.debug("🔐 Loading user details for: {}", userEmail);
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            boolean isTokenValid = !unifiedToken.isRevoked() &&
                    !unifiedToken.isExpired() &&
                    unifiedToken.getExpirationDate() != null &&
                    unifiedToken.getExpirationDate().isAfter(java.time.LocalDateTime.now());

            log.debug("🎫 Token in DB: revoked={}, expired={}, valid={}",
                    unifiedToken.isRevoked(), unifiedToken.isExpired(), isTokenValid);

            if (isTokenValid && userDetails instanceof User) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.info("✅ Authentication set in SecurityContext for user: {}", userEmail);
            } else {
                log.warn("❌ Token validation failed for user: {}", userEmail);
                filterChain.doFilter(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract token from Cookie OR Authorization header
     */
    private String extractToken(HttpServletRequest request) {
        // First try Cookie
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    log.debug("Token extracted from cookie");
                    return cookie.getValue();
                }
            }
        }

        // Then try Authorization header (Bearer token)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            log.debug("Token extracted from Authorization header");
            return authHeader.substring(7);
        }

        return null;
    }
}