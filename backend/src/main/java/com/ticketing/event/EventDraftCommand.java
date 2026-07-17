package com.ticketing.event;

import java.time.Instant;
import java.util.UUID;

/** Fields for creating or editing an event draft. */
public record EventDraftCommand(
        UUID categoryId,
        String title,
        String description,
        EventType eventType,
        String venueName,
        String addressLine,
        String city,
        String onlineUrl,
        String timezone,
        Instant startsAt,
        Instant endsAt,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        int capacity) {
}
