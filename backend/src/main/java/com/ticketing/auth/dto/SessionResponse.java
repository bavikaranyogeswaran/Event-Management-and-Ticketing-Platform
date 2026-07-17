package com.ticketing.auth.dto;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;

import com.ticketing.auth.AppUserDetails;

public record SessionResponse(
        UUID userId,
        String email,
        String displayName,
        List<String> roles,
        boolean emailVerified) {

    public static SessionResponse from(AppUserDetails user) {
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .toList();
        return new SessionResponse(user.userId(), user.getUsername(), user.displayName(), roles, user.emailVerified());
    }
}
