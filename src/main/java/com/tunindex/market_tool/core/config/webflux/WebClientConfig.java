package com.tunindex.market_tool.core.config.webflux;

import com.tunindex.market_tool.core.utils.constants.Constants;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebClientConfig {

    private final ProxyManager proxyManager;

    @Bean
    public WebClient webClient() {
        // Connection pool configuration
        ConnectionProvider connectionProvider = ConnectionProvider.builder("market-tool")
                .maxConnections(100)
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .build();

        // HTTP Client configuration
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Constants.CONNECT_TIMEOUT_MS)
                .responseTimeout(Duration.ofMillis(Constants.READ_TIMEOUT_MS))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(Constants.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(Constants.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                );

        // Configure proxy if enabled
        if (proxyManager.hasProxy()) {
            String proxy = proxyManager.getRandomProxy();
            if (proxy != null) {
                String[] proxyParts = proxy.split(":");
                if (proxyParts.length >= 2) {
                    String host = proxyParts[0];
                    int port = Integer.parseInt(proxyParts[1]);

                    httpClient = httpClient.proxy(proxySpec -> proxySpec
                            .type(ProxyProvider.Proxy.HTTP)
                            .host(host)
                            .port(port));
                    log.info("WebClient configured with proxy: {}:{}", host, port);
                }
            }
        }

        // Memory configuration
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();

        // Default headers
        return WebClient.builder()
                .baseUrl(Constants.INVESTINGCOM_BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies)
                .defaultHeader(Constants.USER_AGENT_HEADER, Constants.DEFAULT_USER_AGENT)
                .defaultHeader(Constants.ACCEPT_HEADER, Constants.DEFAULT_ACCEPT)
                .defaultHeader(Constants.ACCEPT_LANGUAGE_HEADER, Constants.DEFAULT_ACCEPT_LANGUAGE)
                .defaultHeader(Constants.ACCEPT_ENCODING_HEADER, Constants.DEFAULT_ACCEPT_ENCODING)
                .defaultHeader(Constants.CONNECTION_HEADER, Constants.DEFAULT_CONNECTION)
                .build();
    }
}