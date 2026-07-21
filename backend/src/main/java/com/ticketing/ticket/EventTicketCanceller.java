package com.ticketing.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Cancels the tickets of a cancelled event so none can be checked in afterwards. */
@Service
public class EventTicketCanceller {

    private final TicketRepository tickets;
    private final Clock clock;

    EventTicketCanceller(TicketRepository tickets, Clock clock) {
        this.tickets = tickets;
        this.clock = clock;
    }

    /**
     * Voids the still-valid tickets and returns the holders to notify. Runs in the caller's
     * transaction, so the event, its tickets and the notices all commit together or not at all.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> cancelForEvent(UUID eventId) {
        // holders are read first: once the update runs there are no valid tickets left to find
        List<UUID> holders = tickets.findDistinctOwnerIdsByEventIdAndStatus(eventId, TicketStatus.VALID);
        tickets.cancelValidTicketsForEvent(eventId, Instant.now(clock));
        return holders;
    }
}
