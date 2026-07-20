package com.ticketing.order;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;

@Service
public class OrderService {

    private final OrderPlacement placement;
    private final OrderIdempotency idempotency;
    private final OrderRelease orderRelease;
    private final PaidOrderConfirmation paidOrderConfirmation;
    private final OrderRepository orders;

    OrderService(OrderPlacement placement, OrderIdempotency idempotency, OrderRelease orderRelease,
            PaidOrderConfirmation paidOrderConfirmation, OrderRepository orders) {
        this.placement = placement;
        this.idempotency = idempotency;
        this.orderRelease = orderRelease;
        this.paidOrderConfirmation = paidOrderConfirmation;
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

    /** Gives up on an unpaid order and puts its seats back on sale immediately. */
    public OrderResult cancel(UUID orderId, UUID buyerId) {
        return placement.load(orderRelease.cancel(orderId, buyerId), false);
    }

    /**
     * Settles an order whose payment has been recorded. The order must already be locked by
     * the caller. Deliberately has no transaction of its own: it must commit together with
     * the payment that caused it.
     */
    public PaidOrderOutcome confirmPaidOrder(Order lockedOrder) {
        return paidOrderConfirmation.confirm(lockedOrder);
    }

    /**
     * The order a payment may be started for. Whether an order is payable is an order rule,
     * so the payment side asks rather than deciding for itself.
     */
    @Transactional(readOnly = true)
    public Order requirePayableOrder(UUID orderId, UUID buyerId) {
        Order order = orders.findByIdAndUserId(orderId, buyerId)
                .orElseThrow(ResourceNotFoundException::new);
        if (!order.isAwaitingPayment()) {
            throw new ApiException(HttpStatus.CONFLICT, OrderErrorCodes.ORDER_NOT_PAYABLE,
                    "This order is not awaiting payment.");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderResult getOwnedOrder(UUID orderId, UUID buyerId) {
        Order order = orders.findByIdAndUserId(orderId, buyerId)
                .orElseThrow(ResourceNotFoundException::new);
        return placement.load(order, false);
    }
}
