package com.tunindex.market_tool.ads.entities;

import com.tunindex.market_tool.ads.entities.enums.GatedFeature;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * One attempt to watch an ad in order to reach a gated feature.
 *
 * <p>This row is what makes the gate real. The rule is not "the client said it
 * finished the video" - a client can say anything, and a request made with
 * curl says it instantly. The rule is that this row was created at a moment
 * the <em>server</em> recorded, and cannot be completed until that much time
 * has actually passed on the server's clock.
 *
 * <p>Nothing here is taken from the request body except the token that names
 * the row. Duration, start time and completion time are all server-side
 * values, so there is no field a caller can set to make the wait shorter.
 */
@Entity
@Table(name = "ad_gate_sessions", indexes = {
        @Index(name = "idx_gate_token", columnList = "token", unique = true),
        @Index(name = "idx_gate_session_key", columnList = "sessionKey")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdGateSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque handle the client quotes back. Not guessable, single use. */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GatedFeature feature;

    /**
     * Who this view belongs to.
     *
     * <p>A hash of the caller's session, supplied by the gateway. Without it a
     * grant would be a bearer token in the plainest sense: one person could
     * watch one ad and hand the result to everybody.
     */
    @Column(nullable = false, length = 80)
    private String sessionKey;

    @Column(nullable = false)
    private Long adId;

    /** Server clock at issue. The client never sends this. */
    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** How long the creative runs; the minimum wall-clock wait. */
    @Column(nullable = false)
    private int requiredSeconds;

    /**
     * Progress reports received during playback.
     *
     * <p>The elapsed-time check alone can be satisfied by a script that starts
     * a session, sleeps, and posts completion. Requiring the client to check
     * in while the ad runs raises that bar: a bypass has to imitate playback
     * over time rather than wait out a timer.
     */
    @Column(nullable = false)
    private int heartbeats;

    @Column
    private LocalDateTime lastHeartbeatAt;

    @Column
    private LocalDateTime completedAt;

    /** Set once the grant has been issued, so the token cannot mint a second. */
    @Column(nullable = false)
    private boolean consumed;

    /**
     * Whether enough real time has passed for the ad to have been watched.
     *
     * @param now the server's clock, passed in so this stays testable
     */
    public boolean hasRunLongEnough(LocalDateTime now) {
        return Duration.between(startedAt, now).getSeconds() >= requiredSeconds;
    }

    /**
     * Whether playback was reported often enough to be plausible.
     *
     * <p>Expects roughly one check-in per heartbeat interval, less a margin -
     * a dropped ping on a slow connection should not cost someone the ad they
     * just sat through.
     */
    public boolean hasEnoughHeartbeats(int intervalSeconds, int tolerance) {
        int expected = requiredSeconds / Math.max(1, intervalSeconds);
        return heartbeats >= Math.max(0, expected - tolerance);
    }
}
