package com.ticketing.shared.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ErrorCodes;

/** Opaque cursor for keyset pagination sorted by a timestamp then id. Encodes the last row's position. */
public final class KeysetCursor {

    public record Position(Instant timestamp, UUID id) {
    }

    public static String encode(Instant timestamp, UUID id) {
        String raw = timestamp.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null for a blank cursor (first page); throws 400 for a malformed one. */
    public static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf('|');
            Instant timestamp = Instant.parse(raw.substring(0, separator));
            UUID id = UUID.fromString(raw.substring(separator + 1));
            return new Position(timestamp, id);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_CURSOR, "Invalid page cursor.");
        }
    }

    private KeysetCursor() {
    }
}
