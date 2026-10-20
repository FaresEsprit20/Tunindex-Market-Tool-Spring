package com.tunindex.market_tool.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Gives each signed-in caller a stable identifier the services can key on,
 * without handing them the session token itself.
 *
 * <p>The ad gate needs to know who earned a grant, so one person's watched ad
 * does not open a feature for everybody. It cannot use the access token for
 * that - passing a live credential to a service that has no business
 * authenticating anyone would widen the blast radius of any bug in it.
 * A one-way hash identifies the session just as well and is useless if leaked.
 *
 * <p>Derived here rather than accepted from the caller, and any inbound value
 * is overwritten. A header a client could set would let it claim someone
 * else's grant, which is the entire thing the key exists to prevent.
 */
@Component
public class SessionKeyFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Session-Key";
    public static final String ATTRIBUTE = "tunindex.sessionKey";

    private static final String ACCESS_COOKIE = "accessToken";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String sessionKey = deriveSessionKey(exchange.getRequest());

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate();
        if (sessionKey == null) {
            // No session: strip the header entirely rather than pass a blank
            // one, so a service can tell "anonymous" from "claims to be
            // someone". The ad gate refuses the blank case outright.
            builder.headers(headers -> headers.remove(HEADER));
        } else {
            exchange.getAttributes().put(ATTRIBUTE, sessionKey);
            builder.header(HEADER, sessionKey);
        }

        return chain.filter(exchange.mutate().request(builder.build()).build());
    }

    /**
     * A short, stable fingerprint of the caller's access token.
     *
     * <p>Truncated because it only has to distinguish sessions from one
     * another, not resist a search for the original: it is a hash of a value
     * that expires in fifteen minutes, and the full digest would make every
     * grant token longer for no gain.
     */
    static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String deriveSessionKey(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(ACCESS_COOKIE);
        if (cookie != null && !cookie.getValue().isBlank()) {
            return hash(cookie.getValue());
        }
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            return hash(authorization);
        }
        return null;
    }

    @Override
    public int getOrder() {
        // After the client address is settled, before anything that needs to
        // know which session it is dealing with.
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
