package com.ticketing.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrganizerOrderSummary(
        UUID orderId,
        String orderNumber,
        String status,
        BigDecimal grandTotal,
        String currency,
        int ticketCount,
        Instant createdAt) {
}
