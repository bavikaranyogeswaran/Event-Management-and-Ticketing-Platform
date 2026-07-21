package com.ticketing.event;

import java.time.Instant;
import java.util.UUID;

/** One person on an event's door, with enough detail for the organizer to recognise them. */
public record EventStaffMember(UUID userId, String displayName, String email, Instant assignedAt) {
}
