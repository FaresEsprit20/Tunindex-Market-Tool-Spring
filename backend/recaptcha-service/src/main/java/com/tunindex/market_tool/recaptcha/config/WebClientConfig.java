package com.tunindex.market_tool.recaptcha.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    /**
     * For calling other services in this platform by their registered name.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }

    /**
     * For calling the outside world, and Google in particular.
     *
     * <p>This service's only other WebClient is load-balanced, which resolves
     * every hostname through service discovery. Pointed at
     * {@code https://www.google.com/recaptcha/api/siteverify} that means
     * looking for a registered service called "www.google.com", finding none,
     * and failing with "503 Service Unavailable from UNKNOWN" - which reads
     * like Google is down rather than like a misrouted request.
     *
     * <p>The effect was that every verification failed, so reCAPTCHA could
     * never have rejected anything: the api service treats an unreachable
     * verifier as "could not check" and lets the request through.
     *
     * <p>Marked primary so a plain injection of {@code WebClient.Builder} gets
     * this one. The load-balanced client must then be asked for by name, which
     * is the right way round: calling a sibling service is the special case
     * here, and calling the internet is the norm.
     */
    @Bean
    @Primary
    public WebClient.Builder externalWebClientBuilder() {
        return WebClient.builder();
    }
}
