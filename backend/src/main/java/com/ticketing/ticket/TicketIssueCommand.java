package com.ticketing.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One ticket per attendee name, all belonging to a single order line. */
public record TicketIssueCommand(
        UUID orderId,
        UUID orderItemId,
        UUID eventId,
        UUID ticketTypeId,
        UUID ownerUserId,
        List<String> attendeeNames,
        Instant issuedAt) {
}
