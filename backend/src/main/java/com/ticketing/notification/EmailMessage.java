package com.ticketing.notification;

/** One email ready to send: a resolved recipient address, a subject, and a rendered body. */
public record EmailMessage(String to, String subject, String body) {
}
