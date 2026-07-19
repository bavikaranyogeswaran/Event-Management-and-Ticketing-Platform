package com.ticketing.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import com.ticketing.shared.api.ApiException;

/**
 * Stands in for a real provider so order and webhook rules can be tested without the network.
 * Signature checking is reduced to a shared secret, which is enough to prove the handler
 * rejects anything unverified.
 */
public class FakePaymentGateway implements PaymentGateway {

    public static final String VALID_SIGNATURE = "valid-signature";

    private final List<CheckoutRequest> requests = new ArrayList<>();
    private boolean unavailable;
    private PaymentEvent nextEvent;

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.STRIPE;
    }

    @Override
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        if (unavailable) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, PaymentErrorCodes.PAYMENT_GATEWAY_UNAVAILABLE,
                    "The payment provider is not reachable right now.");
        }
        requests.add(request);
        String sessionId = "cs_test_" + UUID.randomUUID();
        return new CheckoutSession(sessionId, "https://checkout.example/" + sessionId);
    }

    @Override
    public PaymentEvent parseEvent(String rawPayload, String signatureHeader) {
        if (!VALID_SIGNATURE.equals(signatureHeader)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PaymentErrorCodes.WEBHOOK_SIGNATURE_INVALID,
                    "Webhook signature could not be verified.");
        }
        return nextEvent;
    }

    // ---- test controls ----

    public void willReturn(PaymentEvent event) {
        this.nextEvent = event;
    }

    public void goOffline(boolean offline) {
        this.unavailable = offline;
    }

    public List<CheckoutRequest> capturedRequests() {
        return List.copyOf(requests);
    }

    public CheckoutRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }

    public void reset() {
        requests.clear();
        unavailable = false;
        nextEvent = null;
    }
}
