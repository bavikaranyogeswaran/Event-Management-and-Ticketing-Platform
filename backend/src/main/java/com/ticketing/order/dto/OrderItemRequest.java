package com.ticketing.order.dto;

import java.util.UUID;

import com.ticketing.order.OrderLine;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull UUID ticketTypeId,
        @Positive int quantity) {

    OrderLine toLine() {
        return new OrderLine(ticketTypeId, quantity);
    }
}
