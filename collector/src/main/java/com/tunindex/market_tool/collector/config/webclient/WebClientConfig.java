package com.tunindex.market_tool.collector.config.webclient;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.SSLException;
import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() throws SSLException {
        // Create SSL context that accepts all certificates (like a browser)
        SslContext sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        HttpClient httpClient = HttpClient.create()
                .secure(spec -> spec.sslContext(sslContext))
                .responseTimeout(Duration.ofSeconds(30))
                .followRedirect(true);

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36")
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .defaultHeader("Accept-Language", "en-US,en;q=0.9")
                .defaultHeader("Accept-Encoding", "gzip, deflate, br")
                .defaultHeader("Connection", "keep-alive")
                .defaultHeader("Upgrade-Insecure-Requests", "1")
                .defaultHeader("Sec-Fetch-Dest", "document")
                .defaultHeader("Sec-Fetch-Mode", "navigate")
                .defaultHeader("Sec-Fetch-Site", "none")
                .defaultHeader("Sec-Fetch-User", "?1")
                .defaultHeader("Cache-Control", "max-age=0")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024 * 5))
                .build();
    }
}