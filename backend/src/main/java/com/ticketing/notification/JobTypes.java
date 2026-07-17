package com.ticketing.notification;

/** Outbox job types and helpers for building unique job keys. */
public final class JobTypes {

    public static final String EMAIL = "EMAIL";

    public static String emailVerificationKey(Object userId) {
        return "EMAIL_VERIFICATION:" + userId;
    }

    private JobTypes() {
    }
}
