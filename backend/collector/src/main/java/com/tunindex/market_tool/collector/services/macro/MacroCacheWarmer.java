package com.tunindex.market_tool.collector.services.macro;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Fills the macro and commodity caches before anyone asks for them.
 *
 * <p>These two endpoints read from several external publishers, and the first
 * call after a restart pays for all of them at once. Measured here, that cold
 * call took 36 seconds - four Yahoo instruments, each waiting out its retry
 * budget while Yahoo was throttling this machine. The api service gives up at
 * twelve, so the request failed, and enough of those in a row tripped the
 * gateway's circuit breaker and took the two dashboard panels down with it.
 *
 * <p>None of that was visible as a cause: the panels simply read "unavailable"
 * for the first minute after every deploy, then started working, which looks
 * like flakiness rather than a cold cache.
 *
 * <p>So the cost is paid here instead, once, by a background thread nobody is
 * waiting on. It also arms the per-host back-off during startup rather than
 * during somebody's page load, so a throttling publisher is already known
 * about by the time the first reader arrives.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MacroCacheWarmer {

    private final MacroIndicatorsService macroIndicatorsService;

    /**
     * Runs after the context is up, off the startup thread.
     *
     * <p>Async on purpose: doing this inline would add half a minute to every
     * boot and delay the service registering with discovery, which trades one
     * outage for a longer one.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        log.info("Priming macro and commodity caches in the background");

        macroIndicatorsService.snapshot()
                .doOnSuccess(snapshot -> log.info("Macro cache primed ({} rates, {} economy, {} currencies)",
                        size(snapshot == null ? null : snapshot.getRates()),
                        size(snapshot == null ? null : snapshot.getEconomy()),
                        size(snapshot == null ? null : snapshot.getCurrencies())))
                // Logged and dropped. A publisher being unreachable at boot is
                // expected occasionally; it must not stop the service starting,
                // and the endpoints already degrade on their own.
                .doOnError(error -> log.warn("Macro cache could not be primed: {}", error.getMessage()))
                .onErrorComplete()
                .subscribe();

        macroIndicatorsService.commodities()
                .doOnSuccess(quotes -> log.info("Commodity cache primed ({} quotes)", size(quotes)))
                .doOnError(error -> log.warn("Commodity cache could not be primed: {}", error.getMessage()))
                .onErrorComplete()
                .subscribe();
    }

    private int size(java.util.List<?> list) {
        return list == null ? 0 : list.size();
    }
}
