package com.ticketing.checkin;

final class CheckInErrorCodes {

    // the scanner is not on this event's door (organizers reach owned events, admins any)
    static final String NOT_ASSIGNED_TO_EVENT = "NOT_ASSIGNED_TO_EVENT";
    static final String TICKET_NOT_FOUND = "TICKET_NOT_FOUND";
    // a real ticket, but for a different event than the one being scanned
    static final String WRONG_EVENT = "WRONG_EVENT";
    static final String TICKET_CANCELLED = "TICKET_CANCELLED";
    static final String ALREADY_CHECKED_IN = "ALREADY_CHECKED_IN";

    private CheckInErrorCodes() {
    }
}
