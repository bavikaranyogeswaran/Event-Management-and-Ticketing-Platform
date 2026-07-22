package com.ticketing.event.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Names the completed banner upload to attach to an event. */
public record SetBannerRequest(@NotNull UUID fileId) {
}
