package com.tunindex.market_tool.ads.service;

import com.tunindex.market_tool.ads.entities.enums.GatedFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Mints and checks the token that says "this person watched the ad".
 *
 * <p>Signed rather than stored, so the gateway can verify a grant without
 * calling this service on every request - a gate that added a network hop to
 * each page load would be its own outage waiting to happen.
 *
 * <p>What the signature covers is the whole point: the feature, whose session
 * it belongs to, and when it stops working. Change any of those and the
 * signature no longer matches, so a caller cannot widen a grant for one
 * feature into a grant for another, hand it to someone else, or push back its
 * expiry.
 */
@Component
@Slf4j
public class AdGrantSigner {

    /**
     * Shared with the gateway, which verifies what this signs.
     *
     * <p>The default exists so the module starts in development. Anything
     * reachable from outside a laptop needs AD_GRANT_SECRET set: a signing key
     * published in a repository signs nothing, because anyone reading it can
     * mint their own grants and skip every ad.
     */
    @Value("${ads.grant.secret:tunindex-dev-ad-grant-secret-change-me}")
    private String secret;

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = ".";

    /**
     * A grant for one feature, one session, until one moment.
     *
     * @return {@code feature.sessionKey.expiryEpochSeconds.signature}
     */
    public String issue(GatedFeature feature, String sessionKey, Instant expiresAt) {
        String payload = payload(feature, sessionKey, expiresAt.getEpochSecond());
        return payload + SEPARATOR + sign(payload);
    }

    /**
     * Whether a grant is genuine, unexpired, and belongs to this caller.
     *
     * <p>Every failure returns the same false. Telling a caller which check
     * failed - bad signature versus expired versus wrong session - hands them
     * a way to probe the scheme one field at a time.
     */
    public boolean isValid(String grant, GatedFeature feature, String sessionKey) {
        if (grant == null || grant.isBlank()) {
            return false;
        }
        String[] parts = grant.split("\\" + SEPARATOR);
        if (parts.length != 4) {
            return false;
        }
        String payload = parts[0] + SEPARATOR + parts[1] + SEPARATOR + parts[2];

        // Signature first: nothing in the payload means anything until we know
        // the payload is ours.
        if (!constantTimeEquals(sign(payload), parts[3])) {
            return false;
        }
        if (!feature.name().equals(parts[0]) || !sessionKey.equals(parts[1])) {
            return false;
        }
        try {
            return Instant.now().getEpochSecond() < Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String payload(GatedFeature feature, String sessionKey, long expiry) {
        return feature.name() + SEPARATOR + sessionKey + SEPARATOR + expiry;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // Signing cannot be skipped on failure: returning a blank or
            // predictable value here would make every grant verify.
            throw new IllegalStateException("Unable to sign ad grant", e);
        }
    }

    /**
     * Compares without leaking how much of the signature matched.
     *
     * <p>An ordinary equals returns as soon as two bytes differ, and the time
     * that takes reveals the length of the correct prefix - enough, given
     * enough attempts, to reconstruct a signature a byte at a time.
     */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
