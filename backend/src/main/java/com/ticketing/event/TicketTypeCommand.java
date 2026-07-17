package com.ticketing.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Fields for creating or editing a ticket type. */
public record TicketTypeCommand(
        String name,
        String description,
        BigDecimal price,
        int quantityTotal,
        int maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt) {
}
