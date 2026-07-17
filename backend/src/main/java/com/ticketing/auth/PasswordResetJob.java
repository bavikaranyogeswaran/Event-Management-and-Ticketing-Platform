package com.ticketing.auth;

/** Payload stored in the outbox for a password-reset email. */
record PasswordResetJob(String to, String displayName, String link) {
}
