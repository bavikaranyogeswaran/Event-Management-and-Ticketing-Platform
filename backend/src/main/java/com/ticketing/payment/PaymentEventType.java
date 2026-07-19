package com.ticketing.payment;

public enum PaymentEventType {
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    /** A genuine delivery about something this platform does not act on; acknowledged and dropped. */
    IGNORED
}
