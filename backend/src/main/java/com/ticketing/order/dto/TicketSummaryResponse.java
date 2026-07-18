package com.ticketing.order.dto;

import java.util.UUID;

import com.ticketing.ticket.Ticket;

/** How a ticket appears inside its order; the validation token is never exposed. */
public record TicketSummaryResponse(UUID id, String publicCode, String attendeeName, String status) {

    public static TicketSummaryResponse from(Ticket ticket) {
        return new TicketSummaryResponse(ticket.getId(), ticket.getPublicCode(),
                ticket.getAttendeeName(), ticket.getStatus().name());
    }
}
