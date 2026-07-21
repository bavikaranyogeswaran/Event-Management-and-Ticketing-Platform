package com.ticketing.notification;

/** Outbox job types and helpers for building unique job keys. */
public final class JobTypes {

    public static final String EMAIL = "EMAIL";

    public static String emailVerificationKey(Object userId) {
        return "EMAIL_VERIFICATION:" + userId;
    }

    // keyed by token id so repeated reset requests each get their own email
    public static String passwordResetKey(Object tokenId) {
        return "PASSWORD_RESET:" + tokenId;
    }

    // one key per order, so a redelivered job never sends a second confirmation
    public static String orderConfirmationKey(Object orderId) {
        return "ORDER_CONFIRMATION:" + orderId;
    }

    // one key per holder per event, so each holder is told once even if cancellation is retried
    public static String eventCancellationKey(Object eventId, Object holderUserId) {
        return "EVENT_CANCELLATION:" + eventId + ":" + holderUserId;
    }

    private JobTypes() {
    }
}
