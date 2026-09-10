package com.tunindex.market_tool.ads.controller;

import com.tunindex.market_tool.ads.entities.enums.GatedFeature;
import com.tunindex.market_tool.ads.service.AdGateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The gate, as the browser sees it.
 *
 * <p>Four steps, and the client controls none of the decisions: open a gate,
 * report progress while the ad plays, ask to finish, and check whether a
 * grant is still held. Whether finishing succeeds is settled by
 * {@link AdGateService} against the server's own clock.
 *
 * <p>Every call is scoped to a session key the gateway supplies. A caller
 * reaching this service directly can put any value there, and it buys nothing:
 * the grant is stamped with that key, and the gateway checks a grant against
 * the key it derives from the caller's own cookie. A grant minted under an
 * invented identity therefore matches nobody.
 */
@RestController
@RequestMapping("/api/v1/ads/gate")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ad gate", description = "Watch-to-unlock enforcement")
public class AdGateController {

    private final AdGateService gateService;

    /** Set by the gateway, which strips any value the caller sent. */
    private static final String SESSION_HEADER = "X-Session-Key";

    /** Where the earned grant is carried back on later requests. */
    public static final String GRANT_COOKIE_PREFIX = "adGrant_";

    /**
     * Opens a gate.
     *
     * <p>204 means there was no ad to show. The client then proceeds straight
     * to the feature: an empty ad server must not become a locked door.
     */
    @PostMapping("/start/{feature}")
    @Operation(summary = "Begin a gated ad view")
    public ResponseEntity<Map<String, Object>> start(@PathVariable GatedFeature feature,
                                                     @RequestHeader(value = SESSION_HEADER, required = false) String sessionKey) {
        String key = requireSession(sessionKey);
        if (key == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return gateService.start(feature, key)
                .<ResponseEntity<Map<String, Object>>>map(challenge -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("ad", challenge.ad());
                    body.put("viewToken", challenge.viewToken());
                    body.put("requiredSeconds", challenge.requiredSeconds());
                    body.put("heartbeatSeconds", challenge.heartbeatSeconds());
                    body.put("feature", feature.name());
                    body.put("featureLabel", feature.getLabel());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Reports that the ad is still playing.
     *
     * <p>Answered with 200 whether or not the check-in counted, because the
     * player has nothing useful to do with the difference and the count is not
     * the client's business. Sending more of them does not help; the server
     * ignores ones that arrive too quickly.
     */
    @PostMapping("/heartbeat")
    @Operation(summary = "Report playback progress")
    public ResponseEntity<Void> heartbeat(@RequestBody TokenRequest request,
                                          @RequestHeader(value = SESSION_HEADER, required = false) String sessionKey) {
        String key = requireSession(sessionKey);
        if (key == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        gateService.heartbeat(request.viewToken(), key);
        return ResponseEntity.ok().build();
    }

    /**
     * Asks for the grant.
     *
     * <p>On success the grant is also set as a cookie, so the browser sends it
     * on subsequent requests without the app having to attach it by hand -
     * which is what lets the gateway enforce the gate on calls the frontend
     * never thought about.
     *
     * <p>A refusal is 403 with the reason and, when the ad simply has not
     * finished, how many seconds remain.
     */
    @PostMapping("/complete")
    @Operation(summary = "Finish a view and claim the grant")
    public ResponseEntity<Map<String, Object>> complete(@RequestBody CompleteRequest request,
                                                        @RequestHeader(value = SESSION_HEADER, required = false) String sessionKey) {
        String key = requireSession(sessionKey);
        if (key == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AdGateService.GateResult result = gateService.complete(request.viewToken(), key);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("granted", result.granted());
        body.put("reason", result.reason());
        if (!result.granted()) {
            body.put("secondsRemaining", result.secondsRemaining());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
        }

        body.put("expiresInSeconds", gateService.grantTtlSeconds());
        return ResponseEntity.ok()
                .header("Set-Cookie", grantCookie(request.feature(), result.grant()))
                .body(body);
    }

    /** Whether this caller may currently use a feature. */
    @GetMapping("/status/{feature}")
    @Operation(summary = "Check whether a grant is held")
    public ResponseEntity<Map<String, Object>> status(@PathVariable GatedFeature feature,
                                                      @RequestHeader(value = SESSION_HEADER, required = false) String sessionKey,
                                                      @RequestHeader(value = "Cookie", required = false) String cookieHeader) {
        String key = requireSession(sessionKey);
        if (key == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String grant = readCookie(cookieHeader, GRANT_COOKIE_PREFIX + feature.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("feature", feature.name());
        body.put("featureLabel", feature.getLabel());
        body.put("granted", gateService.holdsGrant(grant, feature, key));
        return ResponseEntity.ok(body);
    }

    /** The features that are gated at all, so the UI does not hard-code them. */
    @GetMapping("/features")
    @Operation(summary = "List gated features")
    public Map<String, String> features() {
        Map<String, String> out = new LinkedHashMap<>();
        for (GatedFeature feature : GatedFeature.values()) {
            out.put(feature.name(), feature.getLabel());
        }
        return out;
    }

    /**
     * HttpOnly so page scripts cannot read or forge it, SameSite=Lax so it
     * still travels on ordinary navigation. Max-Age matches the grant, so the
     * browser drops it at the moment it stops working rather than sending a
     * dead value.
     */
    private String grantCookie(GatedFeature feature, String grant) {
        return GRANT_COOKIE_PREFIX + feature.name() + "=" + grant
                + "; Path=/; Max-Age=" + gateService.grantTtlSeconds()
                + "; HttpOnly; SameSite=Lax";
    }

    private String readCookie(String cookieHeader, String name) {
        if (cookieHeader == null) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
    }

    /**
     * A blank session key is refused rather than defaulted.
     *
     * <p>Falling back to a shared value would put every anonymous caller in
     * one bucket, and one person's watched ad would open the feature for all
     * of them.
     */
    private String requireSession(String sessionKey) {
        return sessionKey == null || sessionKey.isBlank() ? null : sessionKey;
    }

    public record TokenRequest(String viewToken) {}

    public record CompleteRequest(String viewToken, GatedFeature feature) {}
}
