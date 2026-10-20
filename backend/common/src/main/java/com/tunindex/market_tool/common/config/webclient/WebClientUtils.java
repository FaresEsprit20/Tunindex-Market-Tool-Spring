package com.tunindex.market_tool.common.config.webclient;

import org.springframework.http.HttpHeaders;
import java.util.Arrays;
import java.util.List;

public class WebClientUtils {

    public static final List<String> CHROME_CIPHERS = Arrays.asList(
        "TLS_AES_128_GCM_SHA256",
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        "ECDHE_ECDSA_AES128_GCM_SHA256",
        "ECDHE_RSA_AES128_GCM_SHA256",
        "ECDHE_ECDSA_AES256_GCM_SHA384",
        "ECDHE_RSA_AES256_SHA",
        "AES128_GCM_SHA256",
        "AES256_GCM_SHA384",
        "AES128_SHA",
        "AES256_SHA"
    );

    public static void addStealthHeaders(HttpHeaders headers) {
        headers.add(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
        headers.add(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.add(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,fr;q=0.8,de;q=0.7");
        headers.add(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br, zstd");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.add("Upgrade-Insecure-Requests", "1");
        headers.add(HttpHeaders.CONNECTION, "keep-alive");
    }
}
