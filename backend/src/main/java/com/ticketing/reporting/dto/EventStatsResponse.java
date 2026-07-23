package com.ticketing.reporting.dto;

import java.math.BigDecimal;

public record EventStatsResponse(
        int ticketsSold,
        int remainingCapacity,
        BigDecimal revenue,
        String currency,
        long checkInCount,
        long confirmedOrders,
        long cancelledOrders) {
}
