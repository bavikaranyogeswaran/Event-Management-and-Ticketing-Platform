package com.ticketing.organizer.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Names the completed profile-image upload to use as the organizer's logo. */
public record SetLogoRequest(@NotNull UUID fileId) {
}
