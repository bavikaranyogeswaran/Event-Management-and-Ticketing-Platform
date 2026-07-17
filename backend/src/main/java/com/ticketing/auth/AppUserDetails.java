package com.ticketing.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.ticketing.user.User;
import com.ticketing.user.UserStatus;

/** Security principal wrapping our User; carries just enough to authorize and describe the session. */
public class AppUserDetails implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final String displayName;
    private final boolean emailVerified;
    private final boolean active;
    private final List<GrantedAuthority> authorities;

    private AppUserDetails(User user) {
        this.userId = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.displayName = user.getDisplayName();
        this.emailVerified = user.isEmailVerified();
        this.active = user.getStatus() == UserStatus.ACTIVE;
        this.authorities = user.getRoles().stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r.getRole().name()))
                .toList();
    }

    public static AppUserDetails from(User user) {
        return new AppUserDetails(user);
    }

    public UUID userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }

    public boolean emailVerified() {
        return emailVerified;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
