package com.tunindex.market_tool.gateway.filter;

import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Stamps every request with an id that follows it across services.
 *
 * <p>Without one, a failure that crosses two services produces two unrelated
 * log entries and no way to tell they belong together - which is precisely the
 * kind of failure a gateway makes more common, since a single browser call can
 * now fan out. An id assigned at the door and forwarded downstream is what
 * makes those logs joinable afterwards.
 *
 * <p>An incoming id is honoured rather than replaced, so a caller that already
 * has a trace - a retry, or another service - keeps it.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HEADER, correlationId)
                .build();

        // Echoed back so a user reporting a problem can quote the id from
        // their network tab and it can be found in the logs directly.
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        final String id = correlationId;
        return chain.filter(exchange.mutate().request(request).build())
                // MDC is thread-local and Reactor hops threads freely, so it
                // is set around the subscribe rather than for the whole call.
                .contextWrite(context -> {
                    MDC.put("correlationId", id);
                    return context;
                })
                .doFinally(signal -> MDC.remove("correlationId"));
    }

    @Override
    public int getOrder() {
        // First, so everything logged afterwards carries the id.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
