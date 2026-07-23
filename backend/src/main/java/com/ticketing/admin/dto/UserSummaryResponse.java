package com.ticketing.admin.dto;

import java.time.Instant;
import java.util.UUID;

public record UserSummaryResponse(
        UUID userId,
        String email,
        String displayName,
        String status,
        boolean emailVerified,
        Instant createdAt) {
}
