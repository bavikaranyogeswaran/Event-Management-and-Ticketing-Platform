package com.ticketing.ticket;

/** A rendered ticket file together with the name it should be saved under. */
public record TicketDocument(String fileName, byte[] content) {
}
