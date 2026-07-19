package com.ticketing.payment;

/**
 * Everything provider-specific lives behind this port: hosted checkout and the shape of
 * incoming webhooks. Swapping providers, which Sri Lanka eventually forces, replaces one
 * adapter and leaves the order and payment rules untouched.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /** Opens a hosted payment page for an order that is holding its seats. */
    CheckoutSession createCheckoutSession(CheckoutRequest request);

    /**
     * Verifies a webhook came from the provider and translates it into a neutral event.
     * A request that fails verification is treated as forged, never as a delivery problem.
     */
    PaymentEvent parseEvent(String rawPayload, String signatureHeader);
}
