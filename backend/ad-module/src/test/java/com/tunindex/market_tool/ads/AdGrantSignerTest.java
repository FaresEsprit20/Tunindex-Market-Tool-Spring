package com.tunindex.market_tool.ads;

import com.tunindex.market_tool.ads.entities.enums.GatedFeature;
import com.tunindex.market_tool.ads.service.AdGrantSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grant is the only thing standing between a caller and a gated feature,
 * so what matters is everything it must refuse.
 *
 * <p>A signature check that accepts too much fails silently: the feature just
 * opens, and no log line says why it should not have. Each test here is one
 * way a caller might try to widen a grant it legitimately holds.
 */
@DisplayName("AdGrantSigner")
class AdGrantSignerTest {

    private AdGrantSigner signer;

    private static final String SESSION = "session-key-aaaa";
    private static final String OTHER_SESSION = "session-key-bbbb";

    @BeforeEach
    void setUp() {
        signer = new AdGrantSigner();
        ReflectionTestUtils.setField(signer, "secret", "test-secret-value");
    }

    private Instant soon() {
        return Instant.now().plus(30, ChronoUnit.MINUTES);
    }

    @Test
    @DisplayName("accepts a grant it just issued")
    void acceptsItsOwnGrant() {
        String grant = signer.issue(GatedFeature.PIPELINE_RUN, SESSION, soon());

        assertThat(signer.isValid(grant, GatedFeature.PIPELINE_RUN, SESSION)).isTrue();
    }

    @Test
    @DisplayName("a grant for one feature does not open another")
    void refusesDifferentFeature() {
        // Otherwise the cheapest ad on the site would unlock everything.
        String grant = signer.issue(GatedFeature.DATA_EXPORT, SESSION, soon());

        assertThat(signer.isValid(grant, GatedFeature.PIPELINE_RUN, SESSION)).isFalse();
    }

    @Test
    @DisplayName("a grant cannot be handed to another session")
    void refusesDifferentSession() {
        // Without this one person watches one ad and posts the cookie value
        // somewhere, and nobody else ever watches one again.
        String grant = signer.issue(GatedFeature.PIPELINE_RUN, SESSION, soon());

        assertThat(signer.isValid(grant, GatedFeature.PIPELINE_RUN, OTHER_SESSION)).isFalse();
    }

    @Test
    @DisplayName("an expired grant stops working")
    void refusesExpiredGrant() {
        String grant = signer.issue(GatedFeature.PIPELINE_RUN, SESSION,
                Instant.now().minus(1, ChronoUnit.SECONDS));

        assertThat(signer.isValid(grant, GatedFeature.PIPELINE_RUN, SESSION)).isFalse();
    }

    @Test
    @DisplayName("the expiry cannot be edited to last longer")
    void refusesTamperedExpiry() {
        // The obvious attack once someone sees the token's shape: keep the
        // signature, push the timestamp out.
        String grant = signer.issue(GatedFeature.PIPELINE_RUN, SESSION,
                Instant.now().minus(1, ChronoUnit.HOURS));
        String[] parts = grant.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "."
                + (Instant.now().getEpochSecond() + 99999) + "." + parts[3];

        assertThat(signer.isValid(tampered, GatedFeature.PIPELINE_RUN, SESSION)).isFalse();
    }

    @Test
    @DisplayName("the feature cannot be edited to name a different one")
    void refusesTamperedFeature() {
        String grant = signer.issue(GatedFeature.DATA_EXPORT, SESSION, soon());
        String[] parts = grant.split("\\.");
        String tampered = GatedFeature.PIPELINE_RUN.name() + "." + parts[1] + "."
                + parts[2] + "." + parts[3];

        assertThat(signer.isValid(tampered, GatedFeature.PIPELINE_RUN, SESSION)).isFalse();
    }

    @Test
    @DisplayName("a grant signed with a different key is refused")
    void refusesForeignSignature() {
        AdGrantSigner attacker = new AdGrantSigner();
        ReflectionTestUtils.setField(attacker, "secret", "some-other-secret");
        String forged = attacker.issue(GatedFeature.PIPELINE_RUN, SESSION, soon());

        assertThat(signer.isValid(forged, GatedFeature.PIPELINE_RUN, SESSION)).isFalse();
    }

    @Test
    @DisplayName("malformed input is refused rather than throwing")
    void refusesGarbage() {
        // These arrive from cookies, which anyone can edit to anything. A
        // thrown exception here would be a 500 on every gated request.
        for (String bad : new String[]{null, "", "   ", "a.b.c", "a.b.c.d.e", "not-a-grant",
                "PIPELINE_RUN.session-key-aaaa.notanumber.sig"}) {
            assertThat(signer.isValid(bad, GatedFeature.PIPELINE_RUN, SESSION))
                    .as("should refuse: %s", bad)
                    .isFalse();
        }
    }
}
