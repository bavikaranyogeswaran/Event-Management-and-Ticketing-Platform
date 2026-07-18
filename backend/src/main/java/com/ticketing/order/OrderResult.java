package com.ticketing.order;

import java.util.List;

import com.ticketing.ticket.Ticket;

/** Outcome of placing an order; replay marks a repeat of an order that already existed. */
public record OrderResult(Order order, List<OrderItem> items, List<Ticket> tickets, boolean replay) {
}
