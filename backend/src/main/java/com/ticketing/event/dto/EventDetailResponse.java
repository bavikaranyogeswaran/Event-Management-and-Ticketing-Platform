package com.ticketing.event.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ticketing.event.Event;
import com.ticketing.tickettype.TicketType;

public record EventDetailResponse(
        UUID id,
        String slug,
        String title,
        String description,
        String status,
        String eventType,
        String venueName,
        String addressLine,
        String city,
        String onlineUrl,
        String timezone,
        Instant startsAt,
        Instant endsAt,
        Instant registrationOpensAt,
        Instant registrationClosesAt,
        int capacity,
        UUID categoryId,
        UUID bannerFileId,
        String bannerUrl,
        String rejectionReason,
        Instant publishedAt,
        List<TicketTypeResponse> ticketTypes) {

    public static EventDetailResponse from(Event e, List<TicketType> ticketTypes, String bannerUrl) {
        return new EventDetailResponse(e.getId(), e.getSlug(), e.getTitle(), e.getDescription(), e.getStatus().name(),
                e.getEventType().name(), e.getVenueName(), e.getAddressLine(), e.getCity(), e.getOnlineUrl(),
                e.getTimezone(), e.getStartsAt(), e.getEndsAt(), e.getRegistrationOpensAt(),
                e.getRegistrationClosesAt(), e.getCapacity(), e.getCategoryId(), e.getBannerFileId(), bannerUrl,
                e.getRejectionReason(), e.getPublishedAt(),
                ticketTypes.stream().map(TicketTypeResponse::from).toList());
    }
}
