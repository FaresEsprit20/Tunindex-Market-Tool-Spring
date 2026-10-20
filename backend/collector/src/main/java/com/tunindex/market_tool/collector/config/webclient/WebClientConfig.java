package com.tunindex.market_tool.collector.config.webclient;

import com.tunindex.market_tool.common.filter.*;
import com.tunindex.market_tool.common.config.webclient.WebClientUtils;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.*;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class WebClientConfig {


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
            .defaultHeaders(WebClientUtils::addStealthHeaders)
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
            .ciphers(WebClientUtils.CHROME_CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
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
                spec.maxHeaderListSize(16384);
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


}