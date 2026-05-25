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

}