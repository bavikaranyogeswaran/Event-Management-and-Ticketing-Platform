package com.ticketing.event.dto;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EventRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 5000) String description,
        @NotNull EventType eventType,
        @Size(max = 160) String venueName,
        @Size(max = 255) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 500) String onlineUrl,
        @NotBlank @Size(max = 60) String timezone,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull Instant registrationOpensAt,
        @NotNull Instant registrationClosesAt,
        @Positive int capacity) {

    public EventDraftCommand toCommand() {
        return new EventDraftCommand(categoryId, title, description, eventType, venueName, addressLine, city,
                onlineUrl, timezone, startsAt, endsAt, registrationOpensAt, registrationClosesAt, capacity);
    }
}
