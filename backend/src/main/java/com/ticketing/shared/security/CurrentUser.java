package com.ticketing.shared.security;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ErrorCodes;

/** The logged-in user, handed to controllers that declare it as a parameter. */
public record CurrentUser(UUID userId, String email, Set<Role> roles, boolean emailVerified) {

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
