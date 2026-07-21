package com.ticketing.checkin;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.ticketing.shared.api.ApiException;

/** Shared construction for the "already used" answer, so its shape stays identical everywhere. */
final class CheckInErrors {

    // the original time rides in details, so staff can tell "came in at 18:05" from "forged"
    static ApiException alreadyCheckedIn(Instant checkedInAt) {
        return new ApiException(HttpStatus.CONFLICT, CheckInErrorCodes.ALREADY_CHECKED_IN,
                "This ticket has already been checked in.",
                Map.of("checkedInAt", checkedInAt.toString()));
    }

    private CheckInErrors() {
    }
}
