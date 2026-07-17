package com.ticketing.shared.port;

import java.util.UUID;

/** Port for ID creation so tests can use predictable IDs. */
public interface IdGenerator {

    UUID newId();
}
