package com.ticketing.payment;

import java.util.UUID;

/** What the provider needs to put up a payment page for one order. */
public record CheckoutRequest(
        UUID orderId,
        String orderNumber,
        String description,
        long amountMinorUnits,
        String currency,
        String buyerEmail,
        String successUrl,
        String cancelUrl) {
}
