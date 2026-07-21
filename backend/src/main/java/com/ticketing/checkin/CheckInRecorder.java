package com.ticketing.checkin;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.port.IdGenerator;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketRepository;
import com.ticketing.ticket.TicketStatus;

/** The admitting transaction: one check-in row and the ticket marked used, together or not at all. */
@Service
class CheckInRecorder {

    private final TicketRepository tickets;
    private final CheckInRepository checkIns;
    private final IdGenerator idGenerator;

    CheckInRecorder(TicketRepository tickets, CheckInRepository checkIns, IdGenerator idGenerator) {
        this.tickets = tickets;
        this.checkIns = checkIns;
        this.idGenerator = idGenerator;
    }

    /**
     * The insert is the arbiter: the unique ticket_id lets exactly one of two simultaneous scans win.
     * A loser's saveAndFlush raises a constraint violation that rolls this whole transaction back,
     * so the orchestrator resolves it into the "already used" answer.
     */
    @Transactional
    CheckInReceipt record(UUID ticketId, UUID eventId, UUID staffUserId, CheckInMethod method) {
        Ticket ticket = tickets.findById(ticketId).orElseThrow(ResourceNotFoundException::new);
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, CheckInErrorCodes.TICKET_CANCELLED,
                    "This ticket has been cancelled and cannot be used.");
        }
        // the friendly path for a plain repeat scan; the constraint still guards the concurrent one
        checkIns.findByTicketId(ticketId).ifPresent(existing -> {
            throw CheckInErrors.alreadyCheckedIn(existing.getCheckedInAt());
        });

        CheckIn checkIn = checkIns.saveAndFlush(
                new CheckIn(idGenerator.newId(), ticketId, eventId, staffUserId, method));
        ticket.markUsed();
        tickets.save(ticket);

        Instant checkedInAt = checkIn.getCheckedInAt();
        return new CheckInReceipt(ticketId, ticket.getAttendeeName(), checkedInAt, method);
    }
}
