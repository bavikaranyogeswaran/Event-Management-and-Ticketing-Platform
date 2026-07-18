package com.ticketing.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttendeeRequest(@NotBlank @Size(max = 120) String name) {
}
