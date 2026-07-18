package com.ticketing.order;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.security.TokenService;

/** Decides whether a repeated idempotency key is an honest retry or a different request wearing the same key. */
@Component
class OrderIdempotency {

    private final OrderRepository orders;
    private final TokenService tokenService;

    OrderIdempotency(OrderRepository orders, TokenService tokenService) {
        this.orders = orders;
        this.tokenService = tokenService;
    }

    String fingerprint(OrderCommand command) {
        return tokenService.hash(canonicalize(command));
    }

    /**
     * Returns the original order when this key was already used for the same request.
     * Empty means the key is unused and the caller should create a new order.
     */
    Optional<Order> findReplay(UUID userId, String idempotencyKey, String fingerprint) {
        return orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(existing -> {
                    if (!existing.getRequestHash().equals(fingerprint)) {
                        throw new ApiException(HttpStatus.CONFLICT, OrderErrorCodes.IDEMPOTENCY_CONFLICT,
                                "This idempotency key was already used for a different order.");
                    }
                    return existing;
                });
    }

    private String canonicalize(OrderCommand command) {
        StringBuilder canonical = new StringBuilder(command.eventId().toString());
        // items are sorted so that reordering the same basket stays the same request
        command.items().stream()
                .sorted(Comparator.comparing(line -> line.ticketTypeId().toString()))
                .forEach(line -> canonical.append('|')
                        .append(line.ticketTypeId()).append(':').append(line.quantity()));
        // attendee order decides which name lands on which ticket, so it is kept as sent;
        // the length prefix stops a name containing a separator from imitating a different list
        command.attendees()
                .forEach(name -> canonical.append('|').append(name.length()).append(':').append(name));
        return canonical.toString();
    }
}
