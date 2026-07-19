package com.ticketing.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    // idempotency lookup: an existing key means the request is a replay or a conflict
    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    // owner-scoped fetch; a non-owner gets an empty result, never another user's order
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    // oldest holds first, in batches; matches the partial index on (expires_at) for pending orders
    @Query("""
            SELECT o FROM Order o
            WHERE o.status = com.ticketing.order.OrderStatus.PENDING_PAYMENT
              AND o.expiresAt <= :cutoff
            ORDER BY o.expiresAt ASC
            """)
    List<Order> findDueForExpiry(@Param("cutoff") Instant cutoff, Limit limit);
}
