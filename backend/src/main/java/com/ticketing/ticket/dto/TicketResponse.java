package com.ticketing.ticket.dto;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.event.Event;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketView;

/** A ticket as its owner sees it; the validation token stays server-side. */
public record TicketResponse(
        UUID id,
        String publicCode,
        String attendeeName,
        String status,
        Instant issuedAt,
        UUID orderId,
        UUID eventId,
        String eventTitle,
        Instant eventStartsAt,
        String venueName,
        String city,
        String ticketTypeName) {

    public static TicketResponse from(TicketView view) {
        Ticket ticket = view.ticket();
        Event event = view.event();
        return new TicketResponse(
                ticket.getId(),
                ticket.getPublicCode(),
                ticket.getAttendeeName(),
                ticket.getStatus().name(),
                ticket.getIssuedAt(),
                ticket.getOrderId(),
                event.getId(),
                event.getTitle(),
                event.getStartsAt(),
                event.getVenueName(),
                event.getCity(),
                view.ticketType().getName());
    }
}
