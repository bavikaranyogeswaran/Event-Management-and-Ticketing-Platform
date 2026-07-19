package com.ticketing.payment;

import java.util.UUID;

/**
 * A verified webhook, stripped of provider vocabulary. The amount and currency are carried
 * as the provider reported them so the handler can check them against the order rather than
 * trusting either side.
 */
public record PaymentEvent(
        String eventId,
        PaymentEventType type,
        UUID orderId,
        String providerPaymentId,
        long amountMinorUnits,
        String currency,
        String failureCode) {

    public static PaymentEvent ignored(String eventId) {
        return new PaymentEvent(eventId, PaymentEventType.IGNORED, null, null, 0, null, null);
    }
}
