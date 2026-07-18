package com.ticketing.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ticketing.order.Order;
import com.ticketing.order.OrderResult;

public record OrderResponse(
        UUID id,
        String orderNumber,
        UUID eventId,
        String status,
        String currency,
        BigDecimal subtotal,
        BigDecimal grandTotal,
        Instant confirmedAt,
        List<OrderItemResponse> items,
        List<TicketSummaryResponse> tickets) {

    public static OrderResponse from(OrderResult result) {
        Order order = result.order();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getEventId(),
                order.getStatus().name(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getGrandTotal(),
                order.getConfirmedAt(),
                result.items().stream().map(OrderItemResponse::from).toList(),
                result.tickets().stream().map(TicketSummaryResponse::from).toList());
    }
}
