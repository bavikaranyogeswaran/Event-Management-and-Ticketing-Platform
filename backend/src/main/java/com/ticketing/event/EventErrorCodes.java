package com.ticketing.event;

final class EventErrorCodes {

    static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
    static final String EVENT_DATES_INVALID = "EVENT_DATES_INVALID";
    static final String VENUE_REQUIRED = "VENUE_REQUIRED";
    static final String INVALID_STATE_TRANSITION = "INVALID_STATE_TRANSITION";
    static final String PUBLICATION_RULES_FAILED = "PUBLICATION_RULES_FAILED";
    static final String REASON_REQUIRED = "REASON_REQUIRED";
    static final String EVENT_NOT_EDITABLE = "EVENT_NOT_EDITABLE";
    static final String TICKET_TYPE_PRICE_LOCKED = "TICKET_TYPE_PRICE_LOCKED";
    static final String TICKET_TYPE_QUANTITY_CANNOT_DECREASE = "TICKET_TYPE_QUANTITY_CANNOT_DECREASE";
    // staff are added by email against an account that already exists
    static final String STAFF_USER_NOT_FOUND = "STAFF_USER_NOT_FOUND";
    static final String STAFF_ALREADY_ASSIGNED = "STAFF_ALREADY_ASSIGNED";

    private EventErrorCodes() {
    }
}
