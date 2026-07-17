package com.ticketing.shared.api;

import java.util.Optional;

/** Turns an owner-scoped lookup into a 404 so missing and not-owned look identical to callers. */
public final class Ownership {

    public static <T> T require(Optional<T> found) {
        return found.orElseThrow(ResourceNotFoundException::new);
    }

    private Ownership() {
    }
}
