package com.ticketing.order;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.api.ResourceNotFoundException;

@Service
public class OrderService {

    private final OrderPlacement placement;
    private final OrderIdempotency idempotency;
    private final OrderRepository orders;

    OrderService(OrderPlacement placement, OrderIdempotency idempotency, OrderRepository orders) {
        this.placement = placement;
        this.idempotency = idempotency;
        this.orders = orders;
    }

    /**
     * Places a free order, or returns the original one when the idempotency key has been seen before.
     * Deliberately not transactional: a losing race has to roll back before the winner can be read.
     */
    public OrderResult place(UUID buyerId, String idempotencyKey, OrderCommand command) {
        String fingerprint = idempotency.fingerprint(command);
        Optional<Order> alreadyPlaced = idempotency.findReplay(buyerId, idempotencyKey, fingerprint);
        if (alreadyPlaced.isPresent()) {
            return placement.load(alreadyPlaced.get(), true);
        }
        try {
            return placement.create(buyerId, idempotencyKey, fingerprint, command);
        } catch (DataIntegrityViolationException duplicate) {
            // a parallel request with the same key committed first; serve its order instead of failing
            Order winner = idempotency.findReplay(buyerId, idempotencyKey, fingerprint)
                    .orElseThrow(() -> duplicate);
            return placement.load(winner, true);
        }
    }

    @Transactional(readOnly = true)
    public OrderResult getOwnedOrder(UUID orderId, UUID buyerId) {
        Order order = orders.findByIdAndUserId(orderId, buyerId)
                .orElseThrow(ResourceNotFoundException::new);
        return placement.load(order, false);
    }
}
