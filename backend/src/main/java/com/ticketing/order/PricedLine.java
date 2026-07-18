package com.ticketing.order;

import java.math.BigDecimal;

import com.ticketing.tickettype.TicketType;

/** One validated basket line, priced from the stored ticket type rather than anything the client sent. */
record PricedLine(TicketType ticketType, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
