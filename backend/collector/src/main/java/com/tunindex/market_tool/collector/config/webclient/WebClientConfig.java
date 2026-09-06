package com.tunindex.market_tool.collector.config.webclient;

import com.tunindex.market_tool.collector.filter.*;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebClientConfig {

    private static final List<String> CHROME_CIPHERS = Arrays.asList(
        "TLS_AES_128_GCM_SHA256",
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        "ECDHE_ECDSA_AES128_GCM_SHA256",
        "ECDHE_RSA_AES128_GCM_SHA256",
        "ECDHE_ECDSA_AES256_GCM_SHA384",
        "ECDHE_RSA_AES256_GCM_SHA384",
        "ECDHE_ECDSA_CHACHA20_POLY1305",
        "ECDHE_RSA_CHACHA20_POLY1305",
        "ECDHE_RSA_AES128_SHA",
        "ECDHE_RSA_AES256_SHA",
        "AES128_GCM_SHA256",
        "AES256_GCM_SHA384",
        "AES128_SHA",
        "AES256_SHA"
    );

    @Value("${scraper.proxy.enabled:false}")
    private boolean proxyEnabled;

    @Value("${scraper.proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${scraper.proxy.port:8080}")
    private int proxyPort;

    @Value("${scraper.proxy.username:}")
    private String proxyUser;

    @Value("${scraper.proxy.password:}")
    private String proxyPass;

    @Bean
    public WebClient webClient() throws SSLException {
        if (!OpenSsl.isAvailable()) {
            log.warn("⚠️ OpenSSL/BoringSSL not available, falling back to JDK SSL");
        }

        SslContext sslContext = buildSslContext();
        ConnectionProvider provider = buildConnectionProvider();
        HttpClient httpClient = buildHttpClient(sslContext, provider);

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(64 * 1024 * 1024))
            .defaultHeaders(this::addStealthHeaders)
            .filter(new AdaptiveDelayFilter())
            .filter(new AdvancedRetryFilter())
            .filter(new BrowserEmulationFilter())
            .filter(new CookiePersistenceFilter())
            .filter(new FingerprintRotationFilter())
            .filter(new RequestThrottlingFilter())
            .filter(new ResponseValidationFilter())
            .build();
    }

    private SslContext buildSslContext() throws SSLException {
        return SslContextBuilder.forClient()
            .sslProvider(OpenSsl.isAvailable() ? SslProvider.OPENSSL : SslProvider.JDK)
            .protocols("TLSv1.2", "TLSv1.3")
            .ciphers(CHROME_CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1
            ))
            .sessionCacheSize(0)
            .sessionTimeout(0)
            .enableOcsp(false)
            .build();
    }

    private ConnectionProvider buildConnectionProvider() {
        return ConnectionProvider.builder("stealth-pool")
            .maxConnections(100)
            .maxIdleTime(Duration.ofSeconds(30))
            .maxLifeTime(Duration.ofSeconds(60))
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .evictInBackground(Duration.ofSeconds(15))
            .lifo()
            .metrics(true)
            .build();
    }

    private HttpClient buildHttpClient(SslContext sslContext, ConnectionProvider provider) {
        HttpClient httpClient = HttpClient.create(provider)
            .secure(sslSpec -> sslSpec.sslContext(sslContext))
            .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(ChannelOption.SO_REUSEADDR, true)
            .option(ChannelOption.SO_RCVBUF, 262144)
            .option(ChannelOption.SO_SNDBUF, 262144)
            .option(ChannelOption.IP_TOS, 0x10)
            .http2Settings(spec -> {
                spec.initialWindowSize(65535);
                spec.maxConcurrentStreams(100);
                spec.maxHeaderListSize(8192);
                spec.maxFrameSize(16384);
            })
            .compress(true)
            .keepAlive(true)
            .responseTimeout(Duration.ofSeconds(30))
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)));

        if (proxyEnabled) {
            httpClient = httpClient.proxy(proxySpec -> {
                ProxyProvider.Builder builder = proxySpec
                    .type(ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort);
                if (proxyUser != null && !proxyUser.isBlank()) {
                    builder.username(proxyUser).password(s -> proxyPass);
                }
            });
        }

        return httpClient;
    }

    private void addStealthHeaders(HttpHeaders headers) {
        headers.add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
        headers.add(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,fr;q=0.8,de;q=0.7");
        headers.add(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br, zstd");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add("Upgrade-Insecure-Requests", "1");
        headers.add(HttpHeaders.CONNECTION, "keep-alive");
        headers.add("Priority", "u=0, i");
        headers.add("Sec-Ch-Ua", "\"Google Chrome\";v=\"130\", \"Chromium\";v=\"130\", \"Not_A Brand\";v=\"99\"");
        headers.add("Sec-Ch-Ua-Mobile", "?0");
        headers.add("Sec-Ch-Ua-Platform", "\"Windows\"");
        headers.add("Sec-Ch-Ua-Platform-Version", "\"15.0.0\"");
        headers.add("Sec-Ch-Ua-Arch", "\"x64\"");
        headers.add("Sec-Ch-Ua-Bitness", "64");
        headers.add("Sec-Ch-Ua-Full-Version-List", "\"Google Chrome\";v=\"130.0.6723.59\", \"Chromium\";v=\"130.0.6723.59\", \"Not_A Brand\";v=\"99.0.0.0\"");
        headers.add("Sec-Fetch-Dest", "document");
        headers.add("Sec-Fetch-Mode", "navigate");
        headers.add("Sec-Fetch-Site", "none");
        headers.add("Sec-Fetch-User", "?1");
        headers.add("DNT", "1");
        headers.add("Save-Data", "off");
    }
}