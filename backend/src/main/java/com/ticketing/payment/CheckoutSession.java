package com.ticketing.payment;

/** The provider's answer: where to send the buyer, and how to recognise the session later. */
public record CheckoutSession(String sessionId, String redirectUrl) {
}
