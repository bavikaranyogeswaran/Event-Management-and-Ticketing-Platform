package com.ticketing.user.dto;

import jakarta.validation.constraints.Size;

// null means "leave unchanged"; validation only applies when a value is present
public record UpdateProfileRequest(
        @Size(min = 1, max = 120) String displayName,
        @Size(max = 30) String phone) {
}
