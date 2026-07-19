package com.ticketing.order;

final class OrderErrorCodes {

    static final String EVENT_NOT_ON_SALE = "EVENT_NOT_ON_SALE";
    static final String TICKET_TYPE_NOT_AVAILABLE = "TICKET_TYPE_NOT_AVAILABLE";
    static final String TICKET_INVENTORY_EXHAUSTED = "TICKET_INVENTORY_EXHAUSTED";
    static final String ORDER_LIMIT_EXCEEDED = "ORDER_LIMIT_EXCEEDED";
    static final String IDEMPOTENCY_KEY_REQUIRED = "IDEMPOTENCY_KEY_REQUIRED";
    static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    // free-only until paid checkout lands; a non-zero total is rejected with this code
    static final String PAYMENTS_NOT_ENABLED = "PAYMENTS_NOT_ENABLED";
    // checkout and cancellation only make sense while an order is still awaiting payment
    static final String ORDER_NOT_PAYABLE = "ORDER_NOT_PAYABLE";
    static final String ORDER_NOT_CANCELLABLE = "ORDER_NOT_CANCELLABLE";

    private OrderErrorCodes() {
    }
}
