package com.ticketing.organizer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// null means "leave unchanged"
public record UpdateOrganizerProfileRequest(
        @Size(min = 1, max = 150) String orgName,
        @Size(max = 2000) String description,
        @Email @Size(max = 320) String contactEmail) {
}
