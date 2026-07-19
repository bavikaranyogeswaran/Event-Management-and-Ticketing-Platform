package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTransitionsTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    private Order order() {
        return new Order(UUID.randomUUID(), "ORD-2026-000001", UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("1500.00"), "key", "hash");
    }

    @Test
    void anOrderStartsAwaitingPayment() {
        Order order = order();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.isAwaitingPayment()).isTrue();
    }

    @Test
    void confirmingStampsTheConfirmationTime() {
        Order order = order();
        order.confirm(NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isEqualTo(NOW);
        assertThat(order.isAwaitingPayment()).isFalse();
    }

    @Test
    void expiringAndCancellingAreToldApartByStatus() {
        Order expired = order();
        expired.expire(NOW);
        Order cancelled = order();
        cancelled.cancel(NOW);

        assertThat(expired.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // both record when the order ended, because the table has one timestamp for it
        assertThat(expired.getCancelledAt()).isEqualTo(NOW);
        assertThat(cancelled.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void anOrderWithoutADeadlineIsNeverDue() {
        // free orders confirm immediately and never hold seats against a clock
        assertThat(order().isDueForExpiry(NOW)).isFalse();
    }

    @Test
    void aHeldOrderIsDueOnlyOnceItsDeadlinePasses() {
        Order order = order();
        order.holdUntil(NOW.plus(15, ChronoUnit.MINUTES));

        assertThat(order.isDueForExpiry(NOW)).isFalse();
        assertThat(order.isDueForExpiry(NOW.plus(14, ChronoUnit.MINUTES))).isFalse();
        assertThat(order.isDueForExpiry(NOW.plus(15, ChronoUnit.MINUTES))).isTrue();
        assertThat(order.isDueForExpiry(NOW.plus(16, ChronoUnit.MINUTES))).isTrue();
    }

    @Test
    void anOrderThatIsNoLongerPendingIsNeverDue() {
        // a payment that beat the sweep must not then be expired out from under itself
        Order order = order();
        order.holdUntil(NOW);
        order.confirm(NOW);

        assertThat(order.isDueForExpiry(NOW.plus(1, ChronoUnit.HOURS))).isFalse();
    }
}
