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

    private JobTypes() {
    }
}
