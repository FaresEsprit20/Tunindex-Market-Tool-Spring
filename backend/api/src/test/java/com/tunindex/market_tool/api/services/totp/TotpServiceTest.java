package com.tunindex.market_tool.api.services.totp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base32;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the TOTP implementation against RFC 6238's own test vectors.
 *
 * <p>This is hand-rolled rather than taken from a library, which is a
 * defensible choice - the whole primitive is auditable in one file - but it
 * moves the burden of proof here. "Compatible with any authenticator app" is
 * a claim, and the only way to settle it without a phone is to reproduce the
 * numbers the specification publishes.
 *
 * <p>The vectors below are from RFC 6238 Appendix B, SHA-1 rows, using the
 * spec's seed "12345678901234567890". They are given as eight digits; this
 * implementation emits six, which is the same truncation carried one step
 * further, so the expected values are the last six of each.
 *
 * <p>If these pass, a code produced by Google Authenticator, Authy or
 * 1Password for the same secret and moment is the code this service expects.
 */
@DisplayName("TotpService")
class TotpServiceTest {

    private final TotpService service = new TotpService();

    /** RFC 6238's seed, Base32-encoded as the service stores secrets. */
    private static final String RFC_SECRET =
            new Base32().encodeToString("12345678901234567890".getBytes(StandardCharsets.US_ASCII))
                    .replace("=", "");

    /** Calls the private code generator for a specific time step. */
    private String codeAt(long timeStep) throws Exception {
        Method generate = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        generate.setAccessible(true);
        return (String) generate.invoke(service, RFC_SECRET, timeStep);
    }

    @Test
    @DisplayName("reproduces RFC 6238 Appendix B vectors")
    void matchesSpecificationVectors() throws Exception {
        // T / 30 for each published Unix time, with the expected eight-digit
        // code truncated to the six this service emits.
        assertThat(codeAt(59L / 30)).isEqualTo("287082");
        assertThat(codeAt(1111111109L / 30)).isEqualTo("081804");
        assertThat(codeAt(1111111111L / 30)).isEqualTo("050471");
        assertThat(codeAt(1234567890L / 30)).isEqualTo("005924");
        assertThat(codeAt(2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    @DisplayName("accepts the code valid right now")
    void acceptsCurrentCode() throws Exception {
        String secret = service.generateSecret();
        Method generate = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        generate.setAccessible(true);

        long step = System.currentTimeMillis() / 1000L / 30;
        String current = (String) generate.invoke(service, secret, step);

        assertThat(service.verifyCode(secret, current)).isTrue();
    }

    @Test
    @DisplayName("tolerates a device clock one step out, either way")
    void toleratesClockDrift() throws Exception {
        String secret = service.generateSecret();
        Method generate = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        generate.setAccessible(true);
        long step = System.currentTimeMillis() / 1000L / 30;

        // Phones drift, and a user typing a code as it rolls over is the
        // common case rather than an edge one.
        assertThat(service.verifyCode(secret, (String) generate.invoke(service, secret, step - 1))).isTrue();
        assertThat(service.verifyCode(secret, (String) generate.invoke(service, secret, step + 1))).isTrue();
    }

    @Test
    @DisplayName("rejects a code from well outside the window")
    void rejectsStaleCode() throws Exception {
        String secret = service.generateSecret();
        Method generate = TotpService.class.getDeclaredMethod("generateCode", String.class, long.class);
        generate.setAccessible(true);
        long step = System.currentTimeMillis() / 1000L / 30;

        // Ten steps is five minutes. Accepting this would make a shoulder-
        // surfed code usable long after it appeared.
        assertThat(service.verifyCode(secret, (String) generate.invoke(service, secret, step - 10))).isFalse();
    }

    @Test
    @DisplayName("rejects malformed input rather than throwing")
    void rejectsMalformedInput() {
        String secret = service.generateSecret();

        assertThat(service.verifyCode(secret, null)).isFalse();
        assertThat(service.verifyCode(secret, "")).isFalse();
        assertThat(service.verifyCode(secret, "abcdef")).isFalse();
        assertThat(service.verifyCode(secret, "12345")).isFalse();
        assertThat(service.verifyCode(secret, "1234567")).isFalse();
        assertThat(service.verifyCode(null, "123456")).isFalse();
    }

    @Test
    @DisplayName("generates a 160-bit secret, distinct each time")
    void generatesStrongDistinctSecrets() {
        String first = service.generateSecret();
        String second = service.generateSecret();

        // 20 bytes Base32-encoded is 32 characters once padding is stripped.
        assertThat(first).hasSize(32).matches("[A-Z2-7]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("builds an otpauth URI an authenticator app can read")
    void buildsScannableUri() {
        String secret = service.generateSecret();
        String uri = service.buildOtpAuthUri(secret, "trader@example.com");

        // The parameters apps rely on when scanning; omitting any of them
        // makes a QR code that scans but then produces the wrong codes.
        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=" + secret);
        assertThat(uri).contains("issuer=Tunidex");
        assertThat(uri).contains("algorithm=SHA1");
        assertThat(uri).contains("digits=6");
        assertThat(uri).contains("period=30");
        // The label must carry the account, so a user with several entries
        // can tell them apart.
        assertThat(uri).contains("trader%40example.com");
    }
}
