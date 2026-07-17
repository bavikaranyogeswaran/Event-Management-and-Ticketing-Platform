package com.ticketing.shared.port;

import java.util.UUID;

import org.springframework.stereotype.Component;

/** Production implementation: random UUIDv4. */
@Component
class RandomIdGenerator implements IdGenerator {

    @Override
    public UUID newId() {
        return UUID.randomUUID();
    }
}
