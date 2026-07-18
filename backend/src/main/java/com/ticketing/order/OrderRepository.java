package com.ticketing.order;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // idempotency lookup: an existing key means the request is a replay or a conflict
    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    // owner-scoped fetch; a non-owner gets an empty result, never another user's order
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);
}
