package com.ticketing.payment;

final class PaymentErrorCodes {

    // a request that fails signature checking is treated as forged, never as a delivery problem
    static final String WEBHOOK_SIGNATURE_INVALID = "WEBHOOK_SIGNATURE_INVALID";
    // the provider reported an amount or currency that does not match the stored order
    static final String PAYMENT_AMOUNT_MISMATCH = "PAYMENT_AMOUNT_MISMATCH";
    static final String PAYMENT_GATEWAY_UNAVAILABLE = "PAYMENT_GATEWAY_UNAVAILABLE";

    private PaymentErrorCodes() {
    }
}
