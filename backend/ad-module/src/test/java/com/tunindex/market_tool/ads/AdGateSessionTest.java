package com.tunindex.market_tool.ads;

import com.tunindex.market_tool.ads.entities.AdGateSession;
import com.tunindex.market_tool.ads.entities.enums.GatedFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two checks that decide whether an ad was really watched.
 *
 * <p>Both are the sort of rule that fails open if it is subtly wrong - an
 * off-by-one in either lets a caller finish early, and nothing about the
 * response would look unusual. So the cases worth writing down are the
 * boundaries, not the comfortable middle.
 */
@DisplayName("AdGateSession")
class AdGateSessionTest {

    /** Anchored rather than relative to now, so the assertions cannot drift. */
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 10, 12, 0, 0);

    private AdGateSession session(int requiredSeconds, int heartbeats) {
        return AdGateSession.builder()
                .token("t")
                .feature(GatedFeature.PIPELINE_RUN)
                .sessionKey("session")
                .adId(1L)
                .startedAt(START)
                .requiredSeconds(requiredSeconds)
                .heartbeats(heartbeats)
                .consumed(false)
                .build();
    }

    @Test
    @DisplayName("is not finished a moment before the ad would have ended")
    void refusesJustBeforeTheEnd() {
        AdGateSession session = session(15, 5);

        assertThat(session.hasRunLongEnough(START.plusSeconds(14))).isFalse();
    }

    @Test
    @DisplayName("is finished exactly when the ad's own duration has elapsed")
    void acceptsAtExactlyTheDuration() {
        // The boundary is inclusive on purpose: a player that reports the
        // instant the video ends is honest, and refusing it would make every
        // real completion take one extra second.
        AdGateSession session = session(15, 5);

        assertThat(session.hasRunLongEnough(START.plusSeconds(15))).isTrue();
    }

    @Test
    @DisplayName("cannot be finished instantly, which is the bypass this exists to stop")
    void refusesImmediateCompletion() {
        AdGateSession session = session(15, 0);

        assertThat(session.hasRunLongEnough(START)).isFalse();
    }

    @Test
    @DisplayName("a session with no check-ins at all did not play")
    void refusesSilentSession() {
        // The signature of the sleep-then-complete bypass: the wall clock is
        // satisfied but nothing ever reported playing.
        AdGateSession session = session(15, 0);

        assertThat(session.hasRunLongEnough(START.plusSeconds(20))).isTrue();
        assertThat(session.hasEnoughHeartbeats(3, 2)).isFalse();
    }

    @Test
    @DisplayName("forgives a couple of dropped check-ins")
    void toleratesMissedHeartbeats() {
        // 15s at one check-in every 3s is five expected; three arrived. A
        // stalled connection must not cost someone the ad they just watched.
        AdGateSession session = session(15, 3);

        assertThat(session.hasEnoughHeartbeats(3, 2)).isTrue();
    }

    @Test
    @DisplayName("does not forgive more than the tolerance")
    void refusesTooFewHeartbeats() {
        AdGateSession session = session(15, 2);

        assertThat(session.hasEnoughHeartbeats(3, 2)).isFalse();
    }

    @Test
    @DisplayName("a longer ad expects proportionally more check-ins")
    void scalesWithDuration() {
        // Five check-ins pass for a 15s ad and fail for a 60s one; a fixed
        // threshold would let one short view unlock any length of ad.
        assertThat(session(15, 5).hasEnoughHeartbeats(3, 2)).isTrue();
        assertThat(session(60, 5).hasEnoughHeartbeats(3, 2)).isFalse();
    }
}
