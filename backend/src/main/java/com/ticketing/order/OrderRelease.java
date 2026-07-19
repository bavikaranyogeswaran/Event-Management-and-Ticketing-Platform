package com.ticketing.order;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.tickettype.TicketTypeRepository;

/**
 * The two ways a held order ends without being paid: it runs out of time, or the buyer gives up.
 * Both hand the seats back, so they share one transaction shape and one row lock.
 */
@Service
class OrderRelease {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final TicketTypeRepository ticketTypes;
    private final Clock clock;

    OrderRelease(OrderRepository orders, OrderItemRepository orderItems,
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
        returnSeats(order);
        order.expire(Instant.now(clock));
        orders.save(order);
        return true;
    }

    /** Frees the seats straight away when a buyer abandons checkout. */
    @Transactional
    Order cancel(UUID orderId, UUID buyerId) {
        Order order = orders.findByIdForUpdate(orderId)
                .filter(candidate -> candidate.getUserId().equals(buyerId))
                .orElseThrow(ResourceNotFoundException::new);
        if (!order.isAwaitingPayment()) {
            throw new ApiException(HttpStatus.CONFLICT, OrderErrorCodes.ORDER_NOT_CANCELLABLE,
                    "This order can no longer be cancelled.");
        }
        returnSeats(order);
        order.cancel(Instant.now(clock));
        return orders.save(order);
    }

    private void returnSeats(Order order) {
        for (OrderItem item : orderItems.findByOrderIdOrderByCreatedAtAsc(order.getId())) {
            ticketTypes.release(item.getTicketTypeId(), item.getQuantity());
        }
    }
}
