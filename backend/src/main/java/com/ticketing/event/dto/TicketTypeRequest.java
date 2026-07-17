package com.ticketing.event.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.ticketing.event.TicketTypeCommand;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TicketTypeRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Positive int quantityTotal,
        @Positive int maxPerOrder,
        @NotNull Instant salesStartAt,
        @NotNull Instant salesEndAt) {

    public TicketTypeCommand toCommand() {
        return new TicketTypeCommand(name, description, price, quantityTotal, maxPerOrder, salesStartAt, salesEndAt);
    }
}
