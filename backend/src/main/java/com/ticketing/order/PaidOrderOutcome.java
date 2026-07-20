package com.ticketing.order;

public enum PaidOrderOutcome {
    CONFIRMED,
    /** A previous delivery already settled this order; nothing more to do. */
    ALREADY_CONFIRMED,
    /** Payment arrived after the hold lapsed and the seats had been sold on. */
    SEATS_GONE
}
