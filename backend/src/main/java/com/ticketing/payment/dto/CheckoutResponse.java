package com.ticketing.payment.dto;

import com.ticketing.payment.CheckoutSession;

/** Where to send the buyer to pay. */
public record CheckoutResponse(String checkoutUrl) {

    public static CheckoutResponse from(CheckoutSession session) {
        return new CheckoutResponse(session.redirectUrl());
    }
}
