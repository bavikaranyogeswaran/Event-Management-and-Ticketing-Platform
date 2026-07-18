package com.ticketing.event;

import java.time.Instant;
import java.util.UUID;

/** Optional filters for public event search; any null field is ignored. */
public record PublicEventFilter(UUID categoryId, Instant from, Instant to, String q) {
}
