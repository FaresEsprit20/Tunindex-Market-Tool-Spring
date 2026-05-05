package com.tunindex.market_tool.api.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tunindex.market_tool.api.entities.embedded.Address;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "app_user")
public class User extends AbstractEntity implements UserDetails {

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
    private String password;

    @Embedded
    private Address address;

    @Column(name = "photo")
    private String photo;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
    @JsonIgnore
    private List<Roles> roles;

    @OneToMany(mappedBy = "user")
    private List<UnifiedToken> tokens;

    @Column(name = "locked")
    private Boolean locked = false;

    // Required UserDetails methods
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" +role.getRoleName().name()))
                .toList();
    }

    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return email; }

    // Account status flags (customize as needed)
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() {return !this.locked;}



}
