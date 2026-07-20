package com.ticketing.order;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobService;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.ticket.TicketIssueCommand;
import com.ticketing.ticket.TicketIssuer;
import com.ticketing.tickettype.TicketTypeRepository;

/** Turns a paid order into tickets. Runs inside the transaction that records the payment. */
@Service
class PaidOrderConfirmation {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final TicketTypeRepository ticketTypes;
    private final TicketIssuer ticketIssuer;
    private final OutboxJobService outbox;
    private final Clock clock;

    PaidOrderConfirmation(OrderRepository orders, OrderItemRepository orderItems,
            TicketTypeRepository ticketTypes, TicketIssuer ticketIssuer,
            OutboxJobService outbox, Clock clock) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.ticketTypes = ticketTypes;
        this.ticketIssuer = ticketIssuer;
        this.outbox = outbox;
        this.clock = clock;
    }

    /**
     * Locks the order before deciding, so an expiry sweep running at the same moment either
     * finishes first and is seen here, or waits until this has committed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    PaidOrderOutcome confirm(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow(ResourceNotFoundException::new);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return PaidOrderOutcome.ALREADY_CONFIRMED;
        }

        List<OrderItem> items = orderItems.findByOrderIdOrderByCreatedAtAsc(orderId);
        // an order that stopped waiting gave its seats back, so they have to be won again
        if (!order.isAwaitingPayment() && !reclaimSeats(items)) {
            return PaidOrderOutcome.SEATS_GONE;
        }

        Instant now = Instant.now(clock);
        order.confirm(now);
        orders.save(order);

        int issued = 0;
        for (OrderItem item : items) {
            ticketIssuer.issue(new TicketIssueCommand(order.getId(), item.getId(), order.getEventId(),
                    item.getTicketTypeId(), order.getUserId(), List.of(item.getAttendeeNames()), now));
            issued += item.getQuantity();
        }

        outbox.enqueue(JobTypes.EMAIL, JobTypes.orderConfirmationKey(order.getId()),
                new OrderConfirmationJob(order.getId(), order.getOrderNumber(), order.getUserId(),
                        order.getEventId(), issued));
        return PaidOrderOutcome.CONFIRMED;
    }

    /** All lines or none: a partial claim would quietly hold seats for an order left unconfirmed. */
    private boolean reclaimSeats(List<OrderItem> items) {
        List<OrderItem> claimed = new ArrayList<>();
        for (OrderItem item : items) {
            if (ticketTypes.reserve(item.getTicketTypeId(), item.getQuantity()) == 0) {
                claimed.forEach(taken -> ticketTypes.release(taken.getTicketTypeId(), taken.getQuantity()));
                return false;
            }
            claimed.add(item);
        }
        return true;
    }
}
