package com.ticketing.ticket;

import com.ticketing.event.Event;
import com.ticketing.tickettype.TicketType;

/** A ticket together with the event and type it belongs to, ready for display. */
public record TicketView(Ticket ticket, Event event, TicketType ticketType) {
}
