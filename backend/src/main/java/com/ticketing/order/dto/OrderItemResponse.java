package com.ticketing.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.ticketing.order.OrderItem;

public record OrderItemResponse(
        UUID ticketTypeId,
        String ticketTypeName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getTicketTypeId(), item.getTicketTypeName(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
    }
}
