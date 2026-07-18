package com.ticketing.order;

import java.util.List;
import java.util.UUID;

/** One purchase request: what event, which ticket types, and a name per ticket. */
public record OrderCommand(UUID eventId, List<OrderLine> items, List<String> attendees) {
}
