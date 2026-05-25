package com.tunindex.market_tool.api.config.security.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class IpUaExtractor {

    @Value("${application.security.ip-salt:d6f644b79a844c8a9b32f12e4a7c8d1e5f309a8714b247d5c12e8a7d6f4b29a}")
    private String ipSalt;

    @Value("${application.security.trusted-proxies:}")
    private List<String> trustedProxies;

    public String hashIp(HttpServletRequest request) {
        String ip = getValidatedIp(request);
        return DigestUtils.sha256Hex(ip + ipSalt);
    }

    public String hashUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return DigestUtils.sha256Hex((userAgent != null ? userAgent : "unknown") + ipSalt);
    }

    public String getValidatedIp(HttpServletRequest request) {
        String ipChain = request.getHeader("X-Forwarded-For");
        String clientIp = null;

        if (ipChain != null && !ipChain.isEmpty()) {
            String[] ips = ipChain.split(",");
            for (int i = ips.length - 1; i >= 0; i--) {
                String ip = ips[i].trim();
                if (!isTrustedProxy(ip)) {
                    clientIp = ip;
                    break;
                }
            }
        }

        if (clientIp == null) clientIp = request.getRemoteAddr();
        return clientIp;
    }

    private boolean isTrustedProxy(String ip) {
        return trustedProxies.contains(ip);
    }
}