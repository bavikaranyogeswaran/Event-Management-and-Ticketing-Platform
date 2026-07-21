package com.ticketing.event.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignStaffRequest(@NotBlank @Email @Size(max = 255) String email) {
}
