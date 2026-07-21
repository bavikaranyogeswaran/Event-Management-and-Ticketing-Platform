package com.ticketing.notification;

public enum OutboxStatus {
    PENDING,
    /** Claimed by the relay and in flight to the broker; not eligible to be published again. */
    PUBLISHING,
    SENT,
    /** Retries exhausted; left for a human to inspect. */
    DEAD
}
