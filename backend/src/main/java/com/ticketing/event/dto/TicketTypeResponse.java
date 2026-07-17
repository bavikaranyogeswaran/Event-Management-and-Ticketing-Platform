package com.ticketing.event.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ticketing.tickettype.TicketType;

public record TicketTypeResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String currency,
        int quantityTotal,
        int quantitySold,
        int maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt,
        String status) {

    public static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(t.getId(), t.getName(), t.getDescription(), t.getPrice(), t.getCurrency(),
                t.getQuantityTotal(), t.getQuantitySold(), t.getMaxPerOrder(), t.getSalesStartAt(),
                t.getSalesEndAt(), t.getStatus().name());
    }
}
