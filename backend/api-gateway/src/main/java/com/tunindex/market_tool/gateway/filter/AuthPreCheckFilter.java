package com.tunindex.market_tool.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns away requests that plainly carry no credentials, before they cost a
 * downstream call.
 *
 * <p><b>This is not the authentication.</b> It checks that something
 * credential-shaped is present and does not validate it: no signature check,
 * no expiry check, no database lookup. Each service behind the gateway still
 * runs its own Spring Security exactly as before, so a request that slips past
 * here gains nothing, and a service reached directly on its own port is no
 * less protected than it was.
 *
 * <p>The reason to have it anyway is cost. An unauthenticated flood otherwise
 * reaches the api service, opens a database connection and is rejected there;
 * stopping it at the door keeps that pressure off the services that hold the
 * data. It is a cheap first pass, deliberately shallow enough that nobody is
 * tempted to treat it as the real check.
 *
 * <p>Anything that legitimately has no session - signing in, registering,
 * resetting a password, health probes, API docs - is listed as open. Getting
 * that list wrong locks users out of the login page itself, so it is kept
 * explicit rather than inferred from a pattern.
 */
@Component
@Slf4j
public class AuthPreCheckFilter implements GlobalFilter, Ordered {

    /**
     * Paths reachable without credentials.
     *
     * <p>Matched by prefix on the full request path. Deliberately generous:
     * the cost of wrongly letting a request through is that the service
     * rejects it a moment later, while the cost of wrongly blocking one is a
     * user who cannot sign in at all.
     */
    private static final List<String> OPEN_PATHS = List.of(
            "/auth/authenticate",
            "/auth/refresh-token",
            "/auth/two-factor",
            "/auth/oauth2",
            "/oauth2",
            "/login",
            "/users/create",
            "/users/register",
            "/password-reset",
            "/actuator",
            "/v3/api-docs",
            "/swagger-ui",
            "/fallback"
    );

    /**
     * The opaque session tokens this platform issues: a type prefix and a hex
     * body, as in {@code at_29cf94d7...}. Length is left loose rather than
     * pinned to today's 32 characters, so lengthening the token later does not
     * lock every user out at the gateway.
     */
    private static final Pattern OPAQUE_TOKEN = Pattern.compile("^[a-z]{2,6}_[0-9a-fA-F]{16,128}$");

    /** Three base64url segments, for tokens that arrive from the OAuth2 path. */
    private static final Pattern JWT =
            Pattern.compile("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // A browser preflight carries no credentials by design; rejecting it
        // breaks every cross-origin call before the real request is even sent.
        if (request.getMethod() != null && "OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
            return chain.filter(exchange);
        }

        if (isOpen(path)) {
            return chain.filter(exchange);
        }

        if (hasCredentialShapedThing(request)) {
            return chain.filter(exchange);
        }

        log.debug("Rejecting {} {} at the gateway: no credentials present", request.getMethod(), path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    private boolean isOpen(String path) {
        return OPEN_PATHS.stream().anyMatch(path::contains);
    }

    /**
     * Whether the request carries anything that could be a credential.
     *
     * <p>Cookies count, not just bearer tokens: this platform issues HttpOnly
     * access and refresh cookies, so a perfectly valid request from the
     * browser has no Authorization header at all. Requiring one here would
     * have rejected every authenticated call the app makes.
     *
     * <p>The value is checked for <em>shape</em>, not validity. Any string at
     * all used to count, which had a consequence past the obvious one: a
     * request carrying "Bearer nonsense" got as far as the ad gate and was
     * answered 402 - "watch an ad" - when the honest answer was 401. Turning
     * away a token that could not possibly be one of ours puts the statuses
     * back in the order a caller can act on.
     */
    private boolean hasCredentialShapedThing(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            String token = authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                    ? authorization.substring(7).trim()
                    : authorization.trim();
            return looksLikeOurToken(token);
        }
        return hasTokenCookie(request, "accessToken") || hasTokenCookie(request, "refreshToken");
    }

    private boolean hasTokenCookie(ServerHttpRequest request, String name) {
        HttpCookie cookie = request.getCookies().getFirst(name);
        return cookie != null && looksLikeOurToken(cookie.getValue());
    }

    /**
     * A structural check, and nothing more.
     *
     * <p>Two shapes are issued here: the opaque session tokens, which are a
     * type prefix and a hex body, and JWTs from the OAuth2 path. Anything
     * matching either is passed along for the service to actually verify -
     * this says only that the string is not obvious rubbish.
     *
     * <p>Kept deliberately loose. The cost of wrongly letting something
     * through is that the service rejects it a moment later; the cost of
     * wrongly turning something away is a user who cannot sign in, so where
     * the two are in tension this errs toward letting it through.
     */
    private boolean looksLikeOurToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return OPAQUE_TOKEN.matcher(token).matches() || JWT.matcher(token).matches();
    }

    @Override
    public int getOrder() {
        // After the correlation id, so a rejection is still traceable, and
        // after the rate limiter, so a flood is capped before it is judged.
        return Ordered.HIGHEST_PRECEDENCE + 4;
    }
}
