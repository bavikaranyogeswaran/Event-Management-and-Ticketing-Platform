package com.ticketing.shared.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Standard error body for every non-2xx response. */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldErrorEntry> fieldErrors,
        // extra machine-readable context when an error needs it (e.g. the original check-in time);
        // omitted entirely when empty, so ordinary errors are unchanged
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details,
        String requestId) {

    /** One invalid request field, e.g. "items[0].quantity" -> "must be at most 4". */
    public record FieldErrorEntry(String field, String message) {
    }
}
