package com.tunindex.market_tool.domain.services.fetcher;

import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Random;

public abstract class BaseDataFetcher implements DataFetcherService {

    protected final Random random = new Random();
    protected static final int DELAY_MIN_MS = 3000;
    protected static final int DELAY_MAX_MS = 8000;

    protected long randomDelay() {
        return DELAY_MIN_MS + random.nextInt(DELAY_MAX_MS - DELAY_MIN_MS);
    }

    protected Mono<String> delay() {
        return Mono.delay(Duration.ofMillis(randomDelay())).then(Mono.empty());
    }
}