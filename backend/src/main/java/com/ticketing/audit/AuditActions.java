package com.ticketing.audit;

/** Names of auditable actions. Each module adds the actions it records. */
public final class AuditActions {

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String ACCOUNT_DELETED = "ACCOUNT_DELETED";
    public static final String ORGANIZER_PROFILE_CREATED = "ORGANIZER_PROFILE_CREATED";
    public static final String EVENT_SUBMITTED = "EVENT_SUBMITTED";
    public static final String EVENT_APPROVED = "EVENT_APPROVED";
    public static final String EVENT_REJECTED = "EVENT_REJECTED";
    public static final String EVENT_CANCELLED = "EVENT_CANCELLED";
    public static final String EVENT_STAFF_ASSIGNED = "EVENT_STAFF_ASSIGNED";
    public static final String EVENT_STAFF_REMOVED = "EVENT_STAFF_REMOVED";
    public static final String EXPORT_DOWNLOADED = "EXPORT_DOWNLOADED";
    public static final String EXPORT_GENERATED = "EXPORT_GENERATED";

    private AuditActions() {
    }
}
