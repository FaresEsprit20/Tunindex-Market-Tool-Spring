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
        log.debug("🔍 OAuth2Filter processing: {}", path);

        // Log cookies received
        if (request.getCookies() != null) {
            log.debug("🍪 Received {} cookies", request.getCookies().length);
            for (Cookie cookie : request.getCookies()) {
                log.debug("🍪 Cookie: {} = {}...", cookie.getName(),
                        cookie.getValue().substring(0, Math.min(20, cookie.getValue().length())));
            }
        } else {
            log.debug("❌ NO COOKIES in request to: {}", path);
        }

        // Reject any Authorization header immediately
        if (request.getHeader("Authorization") != null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Authorization headers are not allowed. Use secure cookies only.");
            return;
        }

        String token = extractTokenFromCookie(request);
        if (token == null) {
            log.debug("⏭️ No token cookie found, continuing filter chain");
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("✅ Token cookie found, validating...");

        // Validate token with OAuth2TokenService
        Optional<UnifiedToken> tokenOpt = tokenService.validateToken(token, request);

        if (tokenOpt.isEmpty()) {
            log.warn("❌ Invalid or expired token, clearing cookies");
            clearAllCookies(request, response);
            filterChain.doFilter(request, response);
            return;
        }

        UnifiedToken unifiedToken = tokenOpt.get();
        String userEmail = unifiedToken.getUserEmail();

        log.debug("📧 Extracted email from token: {}", userEmail);

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.debug("🔐 Loading user details for: {}", userEmail);
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            // Check if token is still valid (not revoked, not expired)
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
                // Token is invalid - clear cookies
                clearAllCookies(request, response);
                filterChain.doFilter(request, response);
                return;
            }
        } else if (userEmail == null) {
            log.warn("❌ No userEmail associated with token");
            clearAllCookies(request, response);
            filterChain.doFilter(request, response);
            return;
        } else {
            log.debug("⏭️ Authentication already exists in SecurityContext");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts OAuth2 token from the 'accessToken' cookie only.
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Deletes all cookies from request by setting MaxAge to 0 and value to empty.
     */
    private void clearAllCookies(HttpServletRequest request, HttpServletResponse response) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return;

        for (Cookie cookie : cookies) {
            Cookie clearedCookie = new Cookie(cookie.getName(), "");
            clearedCookie.setMaxAge(0);
            clearedCookie.setPath("/");
            clearedCookie.setHttpOnly(true);
            clearedCookie.setSecure(true);
            response.addCookie(clearedCookie);
            log.debug("Cleared cookie: {}", cookie.getName());
        }

        // Also clear specific auth cookies even if not in request
        String[] authCookies = {"accessToken", "refreshToken", "JSESSIONID"};
        for (String cookieName : authCookies) {
            Cookie clearedCookie = new Cookie(cookieName, "");
            clearedCookie.setMaxAge(0);
            clearedCookie.setPath("/");
            clearedCookie.setHttpOnly(true);
            clearedCookie.setSecure(true);
            response.addCookie(clearedCookie);
        }

        log.info("All authentication cookies cleared");
    }
}