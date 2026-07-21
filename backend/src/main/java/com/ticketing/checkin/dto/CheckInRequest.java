package com.ticketing.checkin.dto;

import java.util.UUID;

import com.ticketing.checkin.CheckInCommand;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One of token or publicCode is expected; the service reports which, if neither is sent. */
public record CheckInRequest(
        @NotNull UUID eventId,
        @Size(max = 512) String token,
        @Size(max = 20) String publicCode) {

    public CheckInCommand toCommand() {
        return new CheckInCommand(eventId, token, publicCode);
    }
}
