package com.tunindex.market_tool.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

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
     */
    private boolean hasCredentialShapedThing(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            return true;
        }
        return request.getCookies().containsKey("accessToken")
                || request.getCookies().containsKey("refreshToken");
    }

    @Override
    public int getOrder() {
        // After the correlation id, so a rejection is still traceable, and
        // after the rate limiter, so a flood is capped before it is judged.
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
