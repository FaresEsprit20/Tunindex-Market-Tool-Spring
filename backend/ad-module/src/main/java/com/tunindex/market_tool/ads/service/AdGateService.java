package com.tunindex.market_tool.ads.service;

import com.tunindex.market_tool.ads.entities.AdEvent;
import com.tunindex.market_tool.ads.entities.AdGateSession;
import com.tunindex.market_tool.ads.entities.Advertisement;
import com.tunindex.market_tool.ads.entities.enums.GatedFeature;
import com.tunindex.market_tool.ads.repository.AdGateSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Enforces that an ad was actually watched before a feature opens.
 *
 * <p>The client is not trusted with any part of this. It cannot say how long
 * the ad was, when it started, or that it finished - those are the server's
 * own record. All it can do is quote a token and be told whether the wait is
 * over. A request built by hand gets exactly the same answer as the real
 * player: not yet.
 *
 * <p>Three things have to hold before a grant is issued, and they close
 * different holes:
 * <ul>
 *   <li><b>Elapsed time on the server clock.</b> Stops the obvious bypass -
 *       calling "complete" immediately.</li>
 *   <li><b>Check-ins during playback.</b> Stops the next one - starting a
 *       session, sleeping, then calling "complete". A bypass now has to
 *       imitate playback for its whole duration, and check-ins arriving
 *       faster than a video can play are not counted.</li>
 *   <li><b>Single use.</b> Stops one watched ad from opening the feature
 *       again and again.</li>
 * </ul>
 *
 * <p>What this deliberately does not claim: it cannot prove a human watched.
 * Nothing server-side can - a script that waits out the duration and sends
 * paced check-ins will earn a grant. The bar it sets is that skipping the ad
 * costs the same time as watching it, which is the point of the ad.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdGateService {

    private final AdGateSessionRepository sessionRepository;
    private final AdService adService;
    private final AdGrantSigner grantSigner;

    /** How often the player is expected to check in. */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 3;

    /**
     * Missed check-ins forgiven before a completion is refused.
     *
     * <p>A dropped ping on a slow connection must not cost somebody the ad
     * they just sat through: the elapsed-time check is the real gate, and this
     * one only has to make a silent script implausible.
     */
    private static final int HEARTBEAT_TOLERANCE = 2;

    /** How long a grant stays good once earned. */
    private static final Duration GRANT_TTL = Duration.ofMinutes(30);

    /** After this, an unfinished session is too old to complete. */
    private static final Duration SESSION_MAX_AGE = Duration.ofMinutes(30);

    /** Used when a creative carries no duration of its own. */
    private static final int DEFAULT_REQUIRED_SECONDS = 15;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** What a caller is told when a gate opens. */
    public record GateChallenge(Advertisement ad, String viewToken,
                                int requiredSeconds, int heartbeatSeconds) {}

    /** The outcome of trying to finish a view. */
    public record GateResult(boolean granted, String grant, String reason,
                             int secondsRemaining) {}

    // -- Opening a gate ---------------------------------------------------

    /**
     * Starts a view for a feature, if there is an ad to show.
     *
     * <p>Empty when no ad is eligible. The caller then lets the feature
     * through: a gate with no inventory behind it must not lock people out of
     * the product, because that costs us a user and earns nothing.
     */
    @Transactional
    public Optional<GateChallenge> start(GatedFeature feature, String sessionKey) {
        return adService.selectForPlacement(feature.getPlacement()).map(ad -> {
            int required = requiredSecondsFor(ad);
            AdGateSession session = sessionRepository.save(AdGateSession.builder()
                    .token(newToken())
                    .feature(feature)
                    .sessionKey(sessionKey)
                    .adId(ad.getId())
                    // The server's own clock. Nothing in the request can move
                    // this earlier, which is what the whole wait rests on.
                    .startedAt(LocalDateTime.now())
                    .requiredSeconds(required)
                    .heartbeats(0)
                    .consumed(false)
                    .build());

            adService.recordEvent(ad.getId(), AdEvent.EventType.IMPRESSION, null, 0, null);
            log.debug("Gate opened for {} ({}s required)", feature, required);
            return new GateChallenge(ad, session.getToken(), required, HEARTBEAT_INTERVAL_SECONDS);
        });
    }

    /**
     * How long this ad must run.
     *
     * <p>A gated ad is never skippable, whatever the campaign says: the
     * feature behind it is the payment. Honouring a skip-after here would let
     * a five-second skip open a fifteen-second gate.
     */
    private int requiredSecondsFor(Advertisement ad) {
        Integer duration = ad.getDurationSeconds();
        return duration == null || duration <= 0 ? DEFAULT_REQUIRED_SECONDS : duration;
    }

    // -- While it plays ---------------------------------------------------

    /**
     * Records that the player is still going.
     *
     * <p>Check-ins arriving faster than the interval are ignored rather than
     * rejected. Ignoring them is what makes the count mean something: a client
     * that fires a hundred in a burst still ends up with the number a real
     * player would have sent, so spamming buys nothing. Rejecting outright
     * would instead punish an honest player whose connection stalled and
     * delivered two pings together.
     */
    @Transactional
    public boolean heartbeat(String token, String sessionKey) {
        return sessionRepository.findByToken(token).map(session -> {
            if (!isUsable(session, sessionKey)) {
                return false;
            }
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime last = session.getLastHeartbeatAt();
            boolean tooSoon = last != null
                    && Duration.between(last, now).getSeconds() < HEARTBEAT_INTERVAL_SECONDS - 1;
            if (!tooSoon) {
                session.setHeartbeats(session.getHeartbeats() + 1);
                session.setLastHeartbeatAt(now);
                sessionRepository.save(session);
            }
            return true;
        }).orElse(false);
    }

    // -- Closing it -------------------------------------------------------

    /**
     * Issues a grant if the ad genuinely ran its course.
     *
     * <p>Refusals carry how long is left, so an honest player that called a
     * moment early can wait and retry rather than start the ad again.
     */
    @Transactional
    public GateResult complete(String token, String sessionKey) {
        Optional<AdGateSession> found = sessionRepository.findByToken(token);
        if (found.isEmpty()) {
            return new GateResult(false, null, "unknown_token", 0);
        }
        AdGateSession session = found.get();

        if (session.isConsumed()) {
            // One ad, one entry. Otherwise a single view is a permanent key.
            return new GateResult(false, null, "already_used", 0);
        }
        if (!sessionKey.equals(session.getSessionKey())) {
            return new GateResult(false, null, "wrong_session", 0);
        }
        LocalDateTime now = LocalDateTime.now();
        if (Duration.between(session.getStartedAt(), now).compareTo(SESSION_MAX_AGE) > 0) {
            return new GateResult(false, null, "expired", 0);
        }
        if (!session.hasRunLongEnough(now)) {
            long left = session.getRequiredSeconds()
                    - Duration.between(session.getStartedAt(), now).getSeconds();
            return new GateResult(false, null, "too_soon", (int) Math.max(1, left));
        }
        if (!session.hasEnoughHeartbeats(HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_TOLERANCE)) {
            log.warn("Gate completion refused for {}: {} check-ins over {}s",
                    session.getFeature(), session.getHeartbeats(), session.getRequiredSeconds());
            return new GateResult(false, null, "not_watched", 0);
        }

        session.setConsumed(true);
        session.setCompletedAt(now);
        sessionRepository.save(session);

        // The event that actually pays: a gated view runs to the end by
        // construction, so it is a completed view rather than an impression.
        adService.recordEvent(session.getAdId(), AdEvent.EventType.COMPLETED_VIEW, null, 100, null);

        String grant = grantSigner.issue(session.getFeature(), sessionKey,
                Instant.now().plus(GRANT_TTL));
        return new GateResult(true, grant, "ok", 0);
    }

    /** Whether a caller already holds a valid grant for a feature. */
    public boolean holdsGrant(String grant, GatedFeature feature, String sessionKey) {
        return grantSigner.isValid(grant, feature, sessionKey);
    }

    public long grantTtlSeconds() {
        return GRANT_TTL.toSeconds();
    }

    // -- Housekeeping -----------------------------------------------------

    private boolean isUsable(AdGateSession session, String sessionKey) {
        return !session.isConsumed()
                && sessionKey.equals(session.getSessionKey())
                && Duration.between(session.getStartedAt(), LocalDateTime.now())
                        .compareTo(SESSION_MAX_AGE) <= 0;
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Drops sessions nobody finished.
     *
     * <p>Abandoning an ad is the normal case - people close tabs - so these
     * rows accumulate steadily and are of no interest once they can no longer
     * be completed.
     */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purgeStaleSessions() {
        LocalDateTime cutoff = LocalDateTime.ofInstant(
                Instant.now().minus(SESSION_MAX_AGE.multipliedBy(2)), ZoneId.systemDefault());
        int removed = sessionRepository.deleteStartedBefore(cutoff);
        if (removed > 0) {
            log.info("Purged {} abandoned ad gate sessions", removed);
        }
    }
}
