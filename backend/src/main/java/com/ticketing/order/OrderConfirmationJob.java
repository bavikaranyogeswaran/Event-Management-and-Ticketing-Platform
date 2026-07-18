package com.ticketing.order;

import java.util.UUID;

/** Outbox payload for the order confirmation email; sent once the email pipeline exists. */
record OrderConfirmationJob(UUID orderId, String orderNumber, UUID buyerId, UUID eventId, int ticketCount) {
}
