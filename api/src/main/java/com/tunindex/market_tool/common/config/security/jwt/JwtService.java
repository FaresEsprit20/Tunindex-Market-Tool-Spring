package com.tunindex.market_tool.common.config.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

@Slf4j
@Service
public class JwtService {

    @Value("${application.security.jwt.private-key}")
    private String privateKeyPath;

    @Value("${application.security.jwt.public-key}")
    private String publicKeyPath;

    @Value("${application.security.jwt.expiration:86400000}")
    private long jwtExpiration;

    @Value("${application.security.jwt.refresh-token.expiration:172800000}")
    private long refreshExpiration;

    @Value("${application.security.ip-salt:d6f644b79a844c8a9b32f12e4a7c8d1e5f309a8714b247d5c12e8a7d6f4b29a}")
    private String ipSalt;

    @Value("${application.security.trusted-proxies:}")
    private List<String> trustedProxies;

    private static final String FAKE_PREFIX = "decoy_";
    private static final List<String> FAKE_SUBJECTS = Arrays.asList("anonymous", "guest", "system", "admin", "user");
    private static final List<String> FAKE_ROLES = Arrays.asList("FAKE_ADMIN", "FAKE_USER", "FAKE_READONLY");
    private static final String FAKE_SECRET = "ThisIsAFakeSecretKeyWith32BytesLength1234";

    private PrivateKey privateKey;
    private PublicKey publicKey;

    // Load RSA keys from classpath
    private void loadKeys() {
        if (privateKey != null && publicKey != null) return;

        try (InputStream privateStream = new ClassPathResource(privateKeyPath).getInputStream();
             InputStream publicStream = new ClassPathResource(publicKeyPath).getInputStream()) {

            byte[] privateBytes = decodePem(privateStream.readAllBytes());
            byte[] publicBytes = decodePem(publicStream.readAllBytes());

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes));

        } catch (Exception e) {
            log.error("Failed to load RSA keys", e);
            throw new RuntimeException("Failed to load RSA keys", e);
        }
    }

    private byte[] decodePem(byte[] keyBytes) {
        String pem = new String(keyBytes)
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(pem);
    }

    // IP & User-Agent hashing
    public String hashIp(String ipAddress) {
        return DigestUtils.sha256Hex(ipAddress + ipSalt);
    }

    public String hashUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return DigestUtils.sha256Hex((userAgent != null ? userAgent : "unknown") + ipSalt);
    }

    // Extract client IP, considering proxies
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
        validateIpFormat(clientIp);
        return clientIp;
    }

    private boolean isTrustedProxy(String ip) {
        return trustedProxies.contains(ip) || trustedProxies.stream().anyMatch(proxy -> ip.startsWith(proxy + "."));
    }

    private void validateIpFormat(String ip) {
        if (ip == null || ip.isEmpty()) {
            throw new SecurityException("IP address cannot be null or empty");
        }
        boolean isValidIpv4 = ip.matches("^(\\d{1,3}\\.){3}\\d{1,3}$");
        boolean isLocalhost = ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1");
        boolean isIpv6 = ip.contains(":");
        if (!isValidIpv4 && !isLocalhost && !isIpv6) {
            throw new SecurityException("Invalid IP address format: " + ip);
        }
    }

    // JWT generation
    public String generateToken(UserDetails userDetails, HttpServletRequest request) {
        return generateToken(new HashMap<>(), userDetails, request);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails, HttpServletRequest request) {
        loadKeys();
        String ipAddress = getValidatedIp(request);
        extraClaims.put("ip_hash", hashIp(ipAddress));
        extraClaims.put("ua_hash", hashUserAgent(request));
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails, HttpServletRequest request) {
        loadKeys();
        Map<String, Object> claims = new HashMap<>();
        String ipAddress = getValidatedIp(request);
        claims.put("ip_hash", hashIp(ipAddress));
        claims.put("ua_hash", hashUserAgent(request));
        return buildToken(claims, userDetails, refreshExpiration);
    }

    private String buildToken(Map<String, Object> claims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    // JWT validation
    public boolean isTokenValid(String token, UserDetails userDetails, HttpServletRequest request) {
        try {
            if (token == null || token.isEmpty()) return false;
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername())
                    && !isTokenExpired(token)
                    && validateTokenClaims(token, request);
        } catch (Exception e) {
            log.error("Token validation error", e);
            return false;
        }
    }

    private boolean validateTokenClaims(String token, HttpServletRequest request) {
        String currentIpHash = hashIp(getValidatedIp(request));
        String currentUaHash = hashUserAgent(request);

        String tokenIpHash = extractClaim(token, claims -> Optional.ofNullable(claims.get("ip_hash", String.class)).orElse(""));
        String tokenUaHash = extractClaim(token, claims -> Optional.ofNullable(claims.get("ua_hash", String.class)).orElse(""));

        return currentIpHash.equals(tokenIpHash) && currentUaHash.equals(tokenUaHash);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        loadKeys();
        final Claims claims = Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claimsResolver.apply(claims);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Fake token generator (decoy)
    public String generateFakeAccessToken() {
        boolean makeExpired = ThreadLocalRandom.current().nextBoolean();
        String fakeIp = ThreadLocalRandom.current().nextInt(256) + "." +
                ThreadLocalRandom.current().nextInt(256) + "." +
                ThreadLocalRandom.current().nextInt(256) + "." +
                ThreadLocalRandom.current().nextInt(256);

        return FAKE_PREFIX + Jwts.builder()
                .setSubject(FAKE_SUBJECTS.get(ThreadLocalRandom.current().nextInt(FAKE_SUBJECTS.size())))
                .claim("isFake", true)
                .claim("roles", Collections.singletonList(
                        FAKE_ROLES.get(ThreadLocalRandom.current().nextInt(FAKE_ROLES.size()))
                ))
                .claim("ip_hash", hashIp(fakeIp))
                .claim("ua_hash", DigestUtils.sha256Hex("fake-user-agent" + ipSalt))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() +
                        (makeExpired ? -1L * ThreadLocalRandom.current().nextInt(10000) : jwtExpiration)))
                .signWith(Keys.hmacShaKeyFor(FAKE_SECRET.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isFakeToken(String token) {
        if (token == null) return false;
        if (token.startsWith(FAKE_PREFIX)) return true;
        try {
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(FAKE_SECRET.getBytes()))
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
