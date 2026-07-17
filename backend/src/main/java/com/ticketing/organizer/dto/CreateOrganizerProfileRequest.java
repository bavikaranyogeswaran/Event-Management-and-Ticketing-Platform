package com.ticketing.organizer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizerProfileRequest(
        @NotBlank @Size(max = 150) String orgName,
        @Size(max = 2000) String description,
        @Email @Size(max = 320) String contactEmail) {
}
