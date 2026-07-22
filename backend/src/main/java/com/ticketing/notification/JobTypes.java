package com.ticketing.notification;

/** Outbox job types and the job-key kinds that decide which email a job renders into. */
public final class JobTypes {

    public static final String EMAIL = "EMAIL";

    // the prefix before the first ':' in a job key; shared by the producers and the renderer
    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String ORDER_CONFIRMATION = "ORDER_CONFIRMATION";
    public static final String EVENT_DECISION = "EVENT_DECISION";
    public static final String EVENT_CANCELLATION = "EVENT_CANCELLATION";
    public static final String REMINDER = "REMINDER";

    public static String emailVerificationKey(Object userId) {
        return EMAIL_VERIFICATION + ":" + userId;
    }

    // keyed by token id so repeated reset requests each get their own email
    public static String passwordResetKey(Object tokenId) {
        return PASSWORD_RESET + ":" + tokenId;
    }

    // one key per order, so a redelivered job never sends a second confirmation
    public static String orderConfirmationKey(Object orderId) {
        return ORDER_CONFIRMATION + ":" + orderId;
    }

    // unique per decision; the organizer's address is resolved when the email is rendered
    public static String eventDecisionKey(Object eventId, Object uniqueId) {
        return EVENT_DECISION + ":" + eventId + ":" + uniqueId;
    }

    // one key per holder per event, so each holder is told once even if cancellation is retried
    public static String eventCancellationKey(Object eventId, Object holderUserId) {
        return EVENT_CANCELLATION + ":" + eventId + ":" + holderUserId;
    }

    // one key per holder per event, so a repeating sweep reminds each holder only once
    public static String reminderKey(Object eventId, Object holderUserId) {
        return REMINDER + ":" + eventId + ":" + holderUserId;
    }

    /** The kind of a job key, e.g. "ORDER_CONFIRMATION" from "ORDER_CONFIRMATION:{id}". */
    public static String kindOf(String jobKey) {
        int separator = jobKey.indexOf(':');
        return separator < 0 ? jobKey : jobKey.substring(0, separator);
    }

    private JobTypes() {
    }
}
