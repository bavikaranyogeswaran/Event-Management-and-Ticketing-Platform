package com.ticketing.shared.api;

import java.time.Instant;
import java.util.List;

/** Standard error body for every non-2xx response. */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldErrorEntry> fieldErrors,
        String requestId) {

    /** One invalid request field, e.g. "items[0].quantity" -> "must be at most 4". */
    public record FieldErrorEntry(String field, String message) {
    }
}
