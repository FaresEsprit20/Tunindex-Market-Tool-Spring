package com.tunindex.market_tool.common.entities;

import com.tunindex.market_tool.common.entities.embedded.Address;
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

}