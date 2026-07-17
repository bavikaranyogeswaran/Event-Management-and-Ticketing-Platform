package com.ticketing.user.dto;

import java.util.List;
import java.util.UUID;

import com.ticketing.user.User;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String phone,
        boolean emailVerified,
        List<String> roles) {

    public static UserProfileResponse from(User user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getRole().name())
                .sorted()
                .toList();
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getPhone(), user.isEmailVerified(), roles);
    }
}
