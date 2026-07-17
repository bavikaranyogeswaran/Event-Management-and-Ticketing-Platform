package com.ticketing.auth;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ErrorCodes;
import com.ticketing.user.Role;

/** The logged-in user, handed to controllers that declare it as a parameter. */
public record CurrentUser(UUID userId, String email, Set<Role> roles, boolean emailVerified) {

    static CurrentUser from(AppUserDetails details) {
        Set<Role> roles = details.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(details.userId(), details.getUsername(), roles, details.emailVerified());
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Guards actions that need a confirmed email, like buying tickets or creating events. */
    public void requireVerifiedEmail() {
        if (!emailVerified) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCodes.EMAIL_NOT_VERIFIED,
                    "Please verify your email address before continuing.");
        }
    }
}
