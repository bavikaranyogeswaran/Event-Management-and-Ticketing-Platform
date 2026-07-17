package com.ticketing.event.dto;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.event.Event;

public record EventSummaryResponse(
        UUID id,
        String slug,
        String title,
        String status,
        String eventType,
        Instant startsAt,
        String city,
        UUID categoryId) {

    public static EventSummaryResponse from(Event e) {
        return new EventSummaryResponse(e.getId(), e.getSlug(), e.getTitle(), e.getStatus().name(),
                e.getEventType().name(), e.getStartsAt(), e.getCity(), e.getCategoryId());
    }
}
