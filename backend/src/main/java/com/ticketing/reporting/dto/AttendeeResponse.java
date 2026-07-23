package com.ticketing.reporting.dto;

import java.time.Instant;
import java.util.UUID;

public record AttendeeResponse(
        UUID ticketId,
        String publicCode,
        String attendeeName,
        String ticketTypeName,
        String status,
        Instant issuedAt,
        Instant checkedInAt) {
}
