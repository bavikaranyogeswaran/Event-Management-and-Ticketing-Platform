package com.ticketing.checkin;

import java.time.Instant;
import java.util.UUID;

/** What a scan reveals about a ticket, whether or not it is being admitted right now. */
public record CheckInView(
        UUID ticketId,
        String attendeeName,
        String ticketTypeName,
        String ticketStatus,
        boolean checkInAllowed,
        Instant checkedInAt) {
}
