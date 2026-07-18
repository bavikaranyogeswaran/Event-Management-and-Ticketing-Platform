package com.ticketing.order.dto;

import java.util.List;
import java.util.UUID;

import com.ticketing.order.OrderCommand;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotNull UUID eventId,
        @NotEmpty @Valid List<OrderItemRequest> items,
        @NotEmpty @Valid List<AttendeeRequest> attendees) {

    public OrderCommand toCommand() {
        return new OrderCommand(eventId,
                items.stream().map(OrderItemRequest::toLine).toList(),
                attendees.stream().map(AttendeeRequest::name).toList());
    }
}
