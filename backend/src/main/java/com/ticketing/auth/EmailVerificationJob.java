package com.ticketing.auth;

/** Payload stored in the outbox for a verification email; consumed once real email sending exists. */
record EmailVerificationJob(String to, String displayName, String link) {
}
