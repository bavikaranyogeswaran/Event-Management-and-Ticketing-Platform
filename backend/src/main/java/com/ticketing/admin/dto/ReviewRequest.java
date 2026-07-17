package com.ticketing.admin.dto;

import com.ticketing.event.ReviewDecision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// reason is required only for REJECTED; that rule is enforced in the service
public record ReviewRequest(
        @NotNull ReviewDecision decision,
        @Size(max = 1000) String reason) {
}
