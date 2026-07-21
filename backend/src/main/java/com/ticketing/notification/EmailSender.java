package com.ticketing.notification;

/**
 * The seam D6 anticipates: one implementation is chosen by configuration, so swapping Gmail for
 * another provider is one class. Throws on failure so the pipeline can record it and retry.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
