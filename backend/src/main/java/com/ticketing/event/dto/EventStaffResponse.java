package com.ticketing.event.dto;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.event.EventStaffMember;

public record EventStaffResponse(UUID userId, String displayName, String email, Instant assignedAt) {

    public static EventStaffResponse from(EventStaffMember member) {
        return new EventStaffResponse(member.userId(), member.displayName(),
                member.email(), member.assignedAt());
    }
}
