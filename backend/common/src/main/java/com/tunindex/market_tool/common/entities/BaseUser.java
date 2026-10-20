package com.tunindex.market_tool.common.entities;

import com.tunindex.market_tool.common.entities.embedded.Address;
import com.tunindex.market_tool.common.entities.enums.TwoFactorMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class) //
public abstract class BaseUser extends AbstractEntity {

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "numTel", unique = true, nullable = false)
    private String numTel;

    @Column(name = "birthdate")
    private LocalDate birthDate;

    @Column(name = "password")
    private String password;  // This is fine - just data, not Security

    @Embedded
    private Address address;

    @Column(name = "photo")
    private String photo;

    @Column(name = "locked")
    private Boolean locked = false;

    // For future OAuth2
    private String provider;
    private String providerId;

    // Named loginName (not "username") on purpose: User.getUsername() is
    // already an @Override for Spring Security's UserDetails contract
    // (returns the email — see User.java), which would silently shadow a
    // Lombok-generated getUsername() for a field literally named
    // "username" and make it unreadable. DB column and DTO/API field are
    // still named "username" for the user-facing concept.
    @Column(name = "username", unique = true)
    private String loginName;

    // TOTP-based two-factor auth (RFC 6238) — configurable per user, off by
    // default. totpSecret is the Base32 shared secret; it stays null until
    // setup and is only ever read server-side to verify a submitted code,
    // never re-sent to the client after initial enrollment.
    // Not DB-level NOT NULL on purpose (like `locked` above): ddl-auto=update
    // adding a NOT NULL column against an already-populated table fails
    // per-row with no default, and Hibernate only warns rather than
    // aborting startup — silently leaving the column missing. Defaulted in
    // Java/at the read sites instead.
    @Column(name = "two_factor_enabled")
    private Boolean twoFactorEnabled = false;

    @Column(name = "totp_secret")
    private String totpSecret;

    /**
     * How the second factor reaches the user.
     *
     * <p>Stored as a string and left nullable for the same reason as the
     * columns above: ddl-auto=update cannot add a NOT NULL column to a table
     * that already has rows, and Hibernate only warns when it fails - leaving
     * the column absent while the application believes it exists. Existing
     * users therefore read as null, which the read sites treat as TOTP: that
     * is what every account enrolled before this field was added is actually
     * using.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "two_factor_method")
    private TwoFactorMethod twoFactorMethod;

    /**
     * The method a pending switch is moving to, held until a code from that
     * channel is confirmed.
     *
     * <p>Kept separate from the live method on purpose. Writing the new
     * method immediately and verifying afterwards would lock the account out
     * whenever the new channel does not work - which, for email, means
     * whenever the mailing service is down.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_two_factor_method")
    private TwoFactorMethod pendingTwoFactorMethod;

    /** The method actually in force, defaulting to what existing users have. */
    public TwoFactorMethod resolvedTwoFactorMethod() {
        return twoFactorMethod == null ? TwoFactorMethod.TOTP : twoFactorMethod;
    }

}