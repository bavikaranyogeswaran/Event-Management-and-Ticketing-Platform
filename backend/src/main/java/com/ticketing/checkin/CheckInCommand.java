package com.ticketing.checkin;

import java.util.UUID;

/** A scan: the event it is being scanned for, and one of a QR token or a typed code. */
public record CheckInCommand(UUID eventId, String token, String publicCode) {
}
