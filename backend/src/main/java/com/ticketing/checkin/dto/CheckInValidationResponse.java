package com.ticketing.checkin.dto;

import java.time.Instant;
import java.util.UUID;

import com.ticketing.checkin.CheckInView;

public record CheckInValidationResponse(
        UUID ticketId,
        String attendeeName,
        String ticketTypeName,
        String ticketStatus,
        boolean checkInAllowed,
        Instant checkedInAt) {

    public static CheckInValidationResponse from(CheckInView view) {
        return new CheckInValidationResponse(view.ticketId(), view.attendeeName(), view.ticketTypeName(),
                view.ticketStatus(), view.checkInAllowed(), view.checkedInAt());
    }
}
