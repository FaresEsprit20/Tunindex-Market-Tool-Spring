package com.tunindex.market_tool.gateway.filter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import com.tunindex.market_tool.gateway.config.AdGateProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Refuses gated features to callers who have not watched the ad.
 *
 * <p>This is the part that makes the gate real rather than decorative. Putting
 * it in the frontend would mean the gate is a screen, and a screen is skipped
 * by calling the endpoint directly - with curl, from the console, or by an app
 * that never drew the screen at all. Enforcing at the gateway means the
 * feature itself is unreachable: there is no request shape that reaches the
 * service without a valid grant.
 *
 * <p>The grant is verified here rather than by asking the ad module, because a
 * gate that needed a network call on every request would add a dependency to
 * every page load and fail whenever the ad service did. A signature can be
 * checked locally with no call at all.
 *
 * <p>Two properties matter and both come from what the signature covers: a
 * grant names one feature, so watching an ad for one thing does not open
 * another; and it names one session, so it cannot be passed to a friend.
 */
@Component
@Slf4j
public class AdGateFilter implements GlobalFilter, Ordered {

    /**
     * Which paths require which feature's grant.
     *
     * <p>Ant patterns, matched against the full path. Configured rather than
     * hard-coded so a gate can be added or lifted without a rebuild - and so
     * the list of what costs an ad is visible in one place instead of spread
     * across controllers.
     */
    private final AdGateProperties properties;

    /** Must match the ad module's signing key, or every grant is refused. */
    @Value("${ads.grant.secret:tunindex-dev-ad-grant-secret-change-me}")
    private String secret;

    public AdGateFilter(AdGateProperties properties) {
        this.properties = properties;
    }

    /**
     * Says out loud what is gated, because the alternative failure is silent.
     *
     * <p>A rule whose key fails to bind - the usual cause being a path written
     * without the brackets Spring's relaxed binding needs - produces no error
     * at all. The filter simply finds no match and every gated endpoint opens,
     * which looks exactly like working software. Printing the rules at startup
     * turns that into something someone can notice.
     */
    @PostConstruct
    void reportRules() {
        Map<String, String> rules = properties.getRules();
        if (!properties.isEnabled()) {
            log.warn("Ad gate is DISABLED - no feature requires watching an ad");
            return;
        }
        if (rules == null || rules.isEmpty()) {
            log.warn("Ad gate is enabled but no rules are configured - nothing is gated");
            return;
        }
        rules.forEach((pattern, feature) -> log.info("Ad gate: {} requires {}", pattern, feature));
    }

    private static final String COOKIE_PREFIX = "adGrant_";
    private static final String ALGORITHM = "HmacSHA256";
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Map<String, String> rules = properties.getRules();
        if (!properties.isEnabled() || rules == null || rules.isEmpty()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String feature = featureFor(rules, path);
        if (feature == null) {
            return chain.filter(exchange);
        }

        // Resolved by SessionKeyFilter from the caller's own cookie. Without a
        // session there is nobody to hold a grant, so the gate cannot open -
        // but the request is let through to be rejected as unauthenticated,
        // which is the more accurate answer and keeps the login flow working.
        Object sessionKey = exchange.getAttribute(SessionKeyFilter.ATTRIBUTE);
        if (!(sessionKey instanceof String key) || key.isBlank()) {
            return chain.filter(exchange);
        }

        HttpCookie grantCookie = exchange.getRequest().getCookies().getFirst(COOKIE_PREFIX + feature);
        String grant = grantCookie == null ? null : grantCookie.getValue();

        if (isValid(grant, feature, key)) {
            return chain.filter(exchange);
        }

        log.debug("Ad gate closed for {} on {}", feature, path);
        return refuse(exchange, feature);
    }

    private String featureFor(Map<String, String> rules, String path) {
        for (Map.Entry<String, String> rule : rules.entrySet()) {
            if (MATCHER.match(rule.getKey(), path)) {
                return rule.getValue();
            }
        }
        return null;
    }

    /**
     * 402 rather than 403, and with the feature named.
     *
     * <p>403 says "not allowed", which a client can only report as an error.
     * 402 says "there is something you can do about this", and the body says
     * what: the frontend reads the feature back and opens that gate, so a call
     * the app never anticipated still leads somewhere useful instead of a dead
     * end.
     */
    private Mono<Void> refuse(ServerWebExchange exchange, String feature) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("httpCode", 402);
        body.put("code", "AD_GATE_REQUIRED");
        body.put("feature", feature);
        body.put("message", "Watch the sponsor message to continue.");

        byte[] bytes = toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.PAYMENT_REQUIRED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** Small enough that a JSON library would be more machinery than it saves. */
    private String toJson(Map<String, Object> map) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                out.append(value);
            } else {
                out.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
            }
        }
        return out.append('}').toString();
    }

    /**
     * Mirrors the ad module's signing exactly.
     *
     * <p>Duplicated rather than shared: the gateway deliberately depends on
     * none of the service modules, and a shared library between them would
     * couple the entry point to the thing it is meant to guard. The format is
     * fixed and the two sides are tested against each other.
     */
    private boolean isValid(String grant, String feature, String sessionKey) {
        if (grant == null || grant.isBlank()) {
            return false;
        }
        String[] parts = grant.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        if (!constantTimeEquals(sign(payload), parts[3])) {
            return false;
        }
        if (!feature.equals(parts[0]) || !sessionKey.equals(parts[1])) {
            return false;
        }
        try {
            return Instant.now().getEpochSecond() < Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify ad grant", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int getOrder() {
        // Last of the entry checks: there is no point deciding whether an ad
        // was watched for a request that is unauthenticated or rate-limited.
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
