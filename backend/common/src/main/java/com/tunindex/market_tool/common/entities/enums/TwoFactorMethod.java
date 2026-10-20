package com.tunindex.market_tool.common.entities.enums;

/**
 * How a user's second factor is delivered.
 *
 * <p>Two-factor auth was previously a single boolean, which could say whether
 * it was on but not how it worked - so the login path hard-coded the
 * authenticator app and the email route, though still present in the code,
 * was unreachable.
 *
 * <p>The two are not equivalent in strength, and the ordering here reflects
 * that: {@link #TOTP} is generated on a device the user holds, while
 * {@link #EMAIL} is only as strong as the inbox it is sent to. Email remains
 * an option because an authenticator app is a real barrier for some users,
 * but it is not the default.
 */
public enum TwoFactorMethod {

    /**
     * A code from an authenticator app (RFC 6238). No delivery channel to
     * fail, works offline, and the secret never leaves the device after
     * enrolment.
     */
    TOTP,

    /**
     * A one-time code sent to the account's email address.
     *
     * <p>Depends on the mailing service being reachable. Switching to it is
     * therefore gated on receiving and confirming a real code first - if
     * delivery is broken, the switch simply does not happen, rather than
     * locking the account behind a message that never arrives.
     */
    EMAIL
}
