package com.tunindex.market_tool.collector.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class BrowserEmulationFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        log.debug("🌐 Emulating browser behavior for: {}", request.url());
        
        return simulateBrowserPreflight()
            .then(next.exchange(request))
            .flatMap(response -> {
                log.debug("📥 Response received with status: {}", response.statusCode());
                return Mono.just(response);
            });
    }

    private Mono<Void> simulateBrowserPreflight() {
        // Simulate DNS resolution (50-150ms)
        long dnsDelay = 50 + ThreadLocalRandom.current().nextInt(100);
        
        // Simulate TCP handshake (50-100ms)
        long tcpDelay = 50 + ThreadLocalRandom.current().nextInt(50);
        
        // Simulate TLS negotiation (100-300ms)
        long tlsDelay = 100 + ThreadLocalRandom.current().nextInt(200);
        
        // Total delay with some randomness
        long totalDelay = dnsDelay + tcpDelay + tlsDelay + 
            ThreadLocalRandom.current().nextInt(50, 200);
        
        return Mono.delay(Duration.ofMillis(totalDelay))
            .doOnSuccess(ignore -> log.debug("✅ Browser preflight completed in {}ms", totalDelay))
            .then();
    }
}
