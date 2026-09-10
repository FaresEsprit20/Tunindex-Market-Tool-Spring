package com.tunindex.market_tool.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Establishes who the caller actually is, and says so downstream.
 *
 * <p>The services behind the gateway bind each session to the client's IP
 * address. A proxy breaks that: the address the service sees is the gateway's,
 * so every user's session binds to one shared value and the binding stops
 * distinguishing anybody - a stolen token replays fine, because it replays
 * from the same address every legitimate request comes from.
 *
 * <p>Spring's own {@code XForwardedHeadersFilter} is deliberately switched off
 * in favour of this one. Its behaviour is governed by a trusted-proxies regex
 * that, when unset, emits no forwarding headers at all, and when set wide
 * enough to cover local callers, passes a client's own
 * {@code X-Forwarded-For} straight through - letting the caller name its own
 * address, which is worse than not forwarding one. Both of those were measured
 * here, not assumed.
 *
 * <p>The rule this applies instead: a header is believed only when the machine
 * that sent it is a configured upstream proxy. From anyone else the claim is
 * discarded and the real socket address is used. Because the header is always
 * overwritten and never appended to, nothing a caller sends can survive.
 */
@Component
@Slf4j
public class ClientAddressFilter implements GlobalFilter, Ordered {

    /** Where the resolved address is published for later filters. */
    public static final String CLIENT_IP_ATTRIBUTE = "tunindex.clientIp";

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";

    /**
     * Addresses of real reverse proxies in front of this gateway.
     *
     * <p>Empty by default, which means "the browser talks to us directly" -
     * the safe assumption, because trusting nobody costs only accuracy behind
     * a proxy that is not there, while trusting the wrong host lets every
     * caller forge its own address. Set it only for hosts that genuinely sit
     * in front, such as an nginx or a load balancer.
     */
    @Value("${gateway.trusted-upstream-proxies:}")
    private List<String> trustedUpstreamProxies = List.of();

    /**
     * Never null, whatever the configuration says.
     *
     * <p>A YAML key written with no value binds to null rather than to an
     * empty list, and a null here would throw on the first request - turning
     * a blank config line into a gateway that rejects all traffic. Treating
     * absent as "no trusted proxies" is also the safe reading.
     */
    private List<String> trustedProxies() {
        return trustedUpstreamProxies == null ? List.of() : trustedUpstreamProxies;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange.getRequest());
        exchange.getAttributes().put(CLIENT_IP_ATTRIBUTE, clientIp);

        ServerHttpRequest request = exchange.getRequest().mutate()
                // set, never add: an inbound value from an untrusted caller
                // must not survive into what the services read.
                .header(X_FORWARDED_FOR, clientIp)
                .header(X_REAL_IP, clientIp)
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String peer = socketAddress(request);

        // Only a known proxy gets to tell us who it is speaking for.
        if (peer != null && trustedProxies().contains(peer)) {
            String claimed = rightmostUntrusted(request.getHeaders().getFirst(X_FORWARDED_FOR));
            if (claimed != null) {
                return claimed;
            }
        }
        return peer == null ? "unknown" : peer;
    }

    /**
     * The last address in the chain that is not itself one of our proxies.
     *
     * <p>Read right to left because each proxy appends the peer it received
     * from: the entries nearest the end are the ones added by infrastructure
     * we control, and everything to the left of them was supplied by whoever
     * called first - which may be the client inventing a chain.
     */
    private String rightmostUntrusted(String chain) {
        if (chain == null || chain.isBlank()) {
            return null;
        }
        String[] hops = chain.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!hop.isEmpty() && !trustedProxies().contains(hop)) {
                return hop;
            }
        }
        return null;
    }

    private String socketAddress(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return null;
        }
        return remote.getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        // After the correlation id so a rejection is traceable, but before
        // the rate limiter, which counts against the address resolved here.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
