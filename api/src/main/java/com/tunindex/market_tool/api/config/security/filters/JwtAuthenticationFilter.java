package com.tunindex.market_tool.api.config.security.filters;

import com.tunindex.market_tool.api.config.security.jwt.JwtService;
import com.tunindex.market_tool.api.repository.JwtTokenRepository;
import io.jsonwebtoken.ExpiredJwtException;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final JwtTokenRepository tokenRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getServletPath();
        log.debug("🔍 JwtFilter processing: {}", path);

        // Skip filter ONLY for public auth endpoints (login, register, password reset)
        // BUT NOT for /check-auth - that needs authentication!
        if (path.contains("/stock/management/v1/auth") && !path.contains("/check-auth")) {
            log.debug("⏭️ Skipping JWT filter for public auth endpoint");
            filterChain.doFilter(request, response);
            return;
        }
        
        if (path.contains("/check-auth")) {
            log.debug("🔐 /check-auth endpoint - JWT validation REQUIRED");
        }

        // Log cookies received
        if (request.getCookies() != null) {
            log.debug("🍪 Received {} cookies", request.getCookies().length);
            for (Cookie cookie : request.getCookies()) {
                log.debug("🍪 Cookie: {} = {}...", cookie.getName(), 
                    cookie.getValue().substring(0, Math.min(20, cookie.getValue().length())));
            }
        } else {
            log.warn("❌ NO COOKIES in request to: {}", path);
        }

        // Reject any Authorization header immediately
        if (request.getHeader("Authorization") != null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Authorization headers are not allowed. Use secure cookies only.");
            return;
        }

        String jwt = extractJwtFromCookie(request);
        if (jwt == null) {
            log.debug("⏭️ No JWT cookie found, continuing filter chain");
            filterChain.doFilter(request, response);
            return;
        }
        
        log.debug("✅ JWT cookie found, validating...");

        String userEmail;
        try {
            userEmail = jwtService.extractUsername(jwt);
            log.debug("📧 Extracted email from JWT: {}", userEmail);
        } catch (ExpiredJwtException e) {
            log.warn("⏰ JWT token expired, clearing cookies");
            // Handle expired token: delete ALL cookies
            clearAllCookies(request, response);
            filterChain.doFilter(request, response);
            return;
        } catch (Exception e) {
            log.error("❌ Error parsing JWT: {}", e.getMessage());
            // Handle other token parsing issues silently
            filterChain.doFilter(request, response);
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            log.debug("🔐 Loading user details for: {}", userEmail);
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
            
            boolean isTokenValid = tokenRepository.findByToken(jwt)
                    .map(token -> {
                        boolean valid = !token.isExpired() && !token.isRevoked();
                        log.debug("🎫 Token in DB: expired={}, revoked={}, valid={}", 
                            token.isExpired(), token.isRevoked(), valid);
                        return valid;
                    })
                    .orElse(false);

            log.debug("🔍 Token validation: isTokenValid={}", isTokenValid);
            
            if (jwtService.isTokenValid(jwt, userDetails, request) && isTokenValid) {
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
            }
        } else if (userEmail == null) {
            log.warn("❌ No userEmail extracted from JWT");
        } else {
            log.debug("⏭️ Authentication already exists in SecurityContext");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT from the 'accessToken' cookie only.
     */
    private String extractJwtFromCookie(HttpServletRequest request) {
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
            clearedCookie.setHttpOnly(cookie.isHttpOnly());
            clearedCookie.setSecure(cookie.getSecure());
            response.addCookie(clearedCookie);
        }
    }

}
