package com.ticketing.checkin;

import java.time.Instant;
import java.util.UUID;

/** Proof that a ticket was admitted, and when. */
public record CheckInReceipt(UUID ticketId, String attendeeName, Instant checkedInAt, CheckInMethod method) {
}
