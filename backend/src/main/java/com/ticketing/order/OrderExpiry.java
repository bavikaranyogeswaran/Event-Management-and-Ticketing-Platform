package com.ticketing.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.tickettype.TicketTypeRepository;

/** Expires a single held order. Separate bean so each order gets its own transaction. */
@Service
class OrderExpiry {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final TicketTypeRepository ticketTypes;
    private final Clock clock;

    OrderExpiry(OrderRepository orders, OrderItemRepository orderItems,
            TicketTypeRepository ticketTypes, Clock clock) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.ticketTypes = ticketTypes;
        this.clock = clock;
    }

    /**
     * Returns the order's seats and marks it expired, or reports false when someone else
     * already settled it. The row lock is taken first so a payment landing at the same
     * moment either wins outright or sees this result — never both at once.
     */
    @Transactional
    boolean expire(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.isAwaitingPayment()) {
            return false; // already paid, cancelled, or swept by an earlier run
        }
        for (OrderItem item : orderItems.findByOrderIdOrderByCreatedAtAsc(orderId)) {
            ticketTypes.release(item.getTicketTypeId(), item.getQuantity());
        }
        order.expire(Instant.now(clock));
        orders.save(order);
        return true;
    }
}
