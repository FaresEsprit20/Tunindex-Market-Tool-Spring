package com.tunindex.market_tool.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A per-caller request ceiling, applied at the door.
 *
 * <p>Deliberately in-memory, and deliberately noted as such. It counts within
 * one gateway instance, so two instances behind a load balancer would each
 * allow the full quota and the effective limit would double. That is an
 * acceptable trade while the gateway is single-instance, and the honest fix
 * when it is not is a shared counter in Redis - which is what Spring Cloud
 * Gateway's own {@code RequestRateLimiter} uses, and why it is not used here:
 * no Redis is running, and a filter that silently fails open would be worse
 * than none.
 *
 * <p>The window is fixed rather than sliding. A caller can therefore send a
 * full quota at the end of one window and another at the start of the next -
 * twice the nominal rate across that boundary. A sliding window costs more
 * bookkeeping than this is worth: the point is to blunt a runaway client or a
 * scraper, not to meter billing.
 */
@Component
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    @Value("${gateway.rate-limit.requests-per-minute:300}")
    private int requestsPerMinute;

    @Value("${gateway.rate-limit.enabled:true}")
    private boolean enabled;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** caller -> count in the current window. */
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    private static final class Counter {
        private volatile Instant windowStart = Instant.now();
        private final AtomicInteger count = new AtomicInteger();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        String caller = callerKey(exchange);
        Counter counter = counters.computeIfAbsent(caller, key -> new Counter());

        Instant now = Instant.now();
        synchronized (counter) {
            if (Duration.between(counter.windowStart, now).compareTo(WINDOW) >= 0) {
                counter.windowStart = now;
                counter.count.set(0);
            }
        }

        int used = counter.count.incrementAndGet();
        if (used > requestsPerMinute) {
            log.warn("Rate limit hit by {} ({} requests in the current minute)", caller, used);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            // Told rather than left to guess: a client that knows when to come
            // back stops hammering, which is the entire point.
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            return exchange.getResponse().setComplete();
        }

        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining",
                String.valueOf(Math.max(0, requestsPerMinute - used)));
        return chain.filter(exchange);
    }

    /**
     * Who to count against.
     *
     * <p>The address {@link ClientAddressFilter} resolved, not the raw socket
     * address. Behind a proxy the socket address is the proxy's, which would
     * collapse every caller onto one counter and let one noisy client exhaust
     * the quota for everybody. That filter is also the only thing permitted to
     * believe a forwarding header, which is what keeps a caller from rewriting
     * its own identity to escape the limit.
     *
     * <p>Falls back to the socket address if the attribute is missing, so a
     * change in filter order degrades the limit rather than removing it.
     */
    private String callerKey(ServerWebExchange exchange) {
        Object resolved = exchange.getAttribute(ClientAddressFilter.CLIENT_IP_ATTRIBUTE);
        if (resolved instanceof String ip && !ip.isBlank()) {
            return ip;
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        // Before the auth pre-check, so a flood of unauthenticated requests is
        // capped rather than merely rejected one at a time - and after
        // ClientAddressFilter, whose resolved address it counts against.
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
