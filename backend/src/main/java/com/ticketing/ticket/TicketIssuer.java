package com.ticketing.ticket;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.port.IdGenerator;

/** Creates the tickets for a confirmed order line. */
@Service
public class TicketIssuer {

    private final TicketRepository tickets;
    private final TicketCodeGenerator codeGenerator;
    private final TicketTokenFactory tokenFactory;
    private final IdGenerator idGenerator;

    TicketIssuer(TicketRepository tickets, TicketCodeGenerator codeGenerator,
            TicketTokenFactory tokenFactory, IdGenerator idGenerator) {
        this.tickets = tickets;
        this.codeGenerator = codeGenerator;
        this.tokenFactory = tokenFactory;
        this.idGenerator = idGenerator;
    }

    /** Joins the caller's transaction so tickets and their order commit or fail together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Ticket> issue(TicketIssueCommand command) {
        List<Ticket> issued = command.attendeeNames().stream()
                .map(attendeeName -> {
                    // the id is fixed first because the validation token is derived from it
                    UUID ticketId = idGenerator.newId();
                    return new Ticket(
                            ticketId,
                            codeGenerator.next(),
                            command.orderId(),
                            command.orderItemId(),
                            command.eventId(),
                            command.ticketTypeId(),
                            command.ownerUserId(),
                            attendeeName.trim(),
                            tokenFactory.tokenHash(ticketId),
                            command.issuedAt());
                })
                .toList();
        return tickets.saveAllAndFlush(issued);
    }
}
