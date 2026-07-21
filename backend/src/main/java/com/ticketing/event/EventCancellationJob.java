package com.ticketing.event;

import java.util.UUID;

/** Outbox payload telling one ticket holder their event was cancelled; sent once the pipeline exists. */
record EventCancellationJob(UUID eventId, String eventTitle, UUID holderUserId) {
}
