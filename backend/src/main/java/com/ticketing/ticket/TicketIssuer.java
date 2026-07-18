package com.ticketing.ticket;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.port.IdGenerator;
import com.ticketing.shared.security.TokenService;

/** Creates the tickets for a confirmed order line. */
@Service
public class TicketIssuer {

    private final TicketRepository tickets;
    private final TicketCodeGenerator codeGenerator;
    private final TokenService tokenService;
    private final IdGenerator idGenerator;

    TicketIssuer(TicketRepository tickets, TicketCodeGenerator codeGenerator,
            TokenService tokenService, IdGenerator idGenerator) {
        this.tickets = tickets;
        this.codeGenerator = codeGenerator;
        this.tokenService = tokenService;
        this.idGenerator = idGenerator;
    }

    /** Joins the caller's transaction so tickets and their order commit or fail together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Ticket> issue(TicketIssueCommand command) {
        List<Ticket> issued = command.attendeeNames().stream()
                .map(attendeeName -> new Ticket(
                        idGenerator.newId(),
                        codeGenerator.next(),
                        command.orderId(),
                        command.orderItemId(),
                        command.eventId(),
                        command.ticketTypeId(),
                        command.ownerUserId(),
                        attendeeName.trim(),
                        tokenService.hash(tokenService.generateRawToken()),
                        command.issuedAt()))
                .toList();
        return tickets.saveAllAndFlush(issued);
    }
}
