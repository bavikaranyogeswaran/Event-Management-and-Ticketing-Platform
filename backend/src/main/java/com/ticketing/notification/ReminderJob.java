package com.ticketing.notification;

import java.util.UUID;

/** Outbox payload for a pre-event reminder; the renderer resolves the holder and event from these ids. */
record ReminderJob(UUID eventId, UUID holderUserId) {
}
