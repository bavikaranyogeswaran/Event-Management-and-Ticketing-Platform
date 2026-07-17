package com.ticketing.event;

import java.util.UUID;

/** Outbox payload for notifying an organizer of an approval or rejection; sent once the email pipeline exists. */
record EventDecisionJob(UUID eventId, String eventTitle, UUID organizerId, String decision, String reason) {
}
