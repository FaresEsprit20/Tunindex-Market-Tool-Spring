package com.tunindex.market_tool.api.config.webclient;

import com.tunindex.market_tool.common.config.webclient.WebClientUtils;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;

@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder() throws SSLException {
        // Configure HttpClient to mimic browser behavior and avoid timeouts
        HttpClient httpClient = HttpClient.create()
            .secure(sslSpec -> {
                try {
                    sslSpec.sslContext(SslContextBuilder.forClient()
                        .sslProvider(SslProvider.JDK)
                        .ciphers(WebClientUtils.CHROME_CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE) // For development/avoiding SSL issues
                        .build());
                } catch (SSLException e) {
                    log.error("❌ Failed to create SSL context", e);
                }
            })
            .option(ChannelOption.TCP_NODELAY, true)
            .compress(true);

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeaders(WebClientUtils::addStealthHeaders);
    }
}
