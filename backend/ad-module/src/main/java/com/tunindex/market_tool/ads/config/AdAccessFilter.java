package com.tunindex.market_tool.ads.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Decides who may reach which part of the ad service.
 *
 * <p>Until now, nothing did. Every endpoint was open to anyone who could reach
 * the port, which meant a plain unauthenticated request could create campaigns,
 * rewrite their rates, or delete the lot - and the revenue report was readable
 * by anybody who asked. The gateway in front helped only against the public
 * internet, and only for as long as nothing else could route to port 8090.
 *
 * <p>Two kinds of caller, so two rules:
 *
 * <ul>
 *   <li><b>Delivery</b> - fetching an ad to show, reporting that it was shown,
 *       and the watch-to-unlock flow. Needs the session key the gateway
 *       derives from the caller's own cookie and refuses to accept from the
 *       caller. That is not a full authentication, and is not meant to be: the
 *       gateway strips any inbound value, so the key's presence means a real
 *       session cookie was on the request.</li>
 *   <li><b>Management and reporting</b> - inventory, rates, revenue. Needs the
 *       internal API key, the same shared secret the collector uses for its
 *       own internal endpoints. That deliberately keeps this out of reach of a
 *       browser: when an admin screen is built it should go through the api
 *       service, which knows about roles, rather than shipping this secret to
 *       a page where anyone can read it.</li>
 * </ul>
 */
@Component
@Slf4j
public class AdAccessFilter extends OncePerRequestFilter {

    @Value("${internal.api.key}")
    private String internalApiKey;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final String SESSION_HEADER = "X-Session-Key";
    private static final String API_KEY_HEADER = "X-API-Key";

    /** Everything this filter guards. Anything else is somebody else's. */
    private static final String GUARDED = "/api/v1/ads/**";

    /**
     * Reachable by any caller with a session.
     *
     * <p>Listed as method and pattern together, because the difference between
     * serving an ad and editing one is partly the verb: {@code GET /ads/{id}}
     * reads a campaign's rate and budget and belongs to management, while
     * {@code POST /ads/{id}/events} is an ordinary viewer reporting an
     * impression.
     */
    private record Rule(HttpMethod method, String pattern) {
        boolean matches(String method, String path) {
            return (this.method == null || this.method.name().equals(method))
                    && MATCHER.match(pattern, path);
        }
    }

    private static final List<Rule> DELIVERY = List.of(
            new Rule(HttpMethod.GET, "/api/v1/ads/serve/*"),
            new Rule(HttpMethod.POST, "/api/v1/ads/*/events"),
            // The gate's own endpoints check the session key again and tie it
            // to the view they issue; this only keeps anonymous callers out.
            new Rule(null, "/api/v1/ads/gate/**"),
            // Enum labels for the UI. No campaign data, no revenue.
            new Rule(HttpMethod.GET, "/api/v1/ads/options")
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (!MATCHER.match(GUARDED, path)) {
            chain.doFilter(request, response);
            return;
        }

        // A browser preflight carries no credentials by design; rejecting it
        // fails the real request before it is ever sent.
        if (HttpMethod.OPTIONS.matches(method)) {
            chain.doFilter(request, response);
            return;
        }

        boolean delivery = DELIVERY.stream().anyMatch(rule -> rule.matches(method, path));
        if (delivery) {
            if (hasText(request.getHeader(SESSION_HEADER))) {
                chain.doFilter(request, response);
            } else {
                deny(response, path, "a signed-in session");
            }
            return;
        }

        if (isValidApiKey(request.getHeader(API_KEY_HEADER))) {
            chain.doFilter(request, response);
        } else {
            deny(response, path, "the internal API key");
        }
    }

    /**
     * Compared without short-circuiting on the first wrong character.
     *
     * <p>An ordinary equals returns as soon as two bytes differ, and how long
     * that takes leaks the length of the correct prefix - enough, given enough
     * attempts, to recover the key one character at a time.
     */
    private boolean isValidApiKey(String provided) {
        if (!hasText(provided) || internalApiKey == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                internalApiKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The same 401 either way, saying what was needed but not what was wrong.
     *
     * <p>Distinguishing "no key" from "wrong key" would tell someone probing
     * the endpoint whether they were close.
     */
    private void deny(HttpServletResponse response, String path, String required) throws IOException {
        log.warn("Refused {}: requires {}", path, required);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"httpCode\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"This endpoint requires "
                        + required + ".\"}");
    }
}
