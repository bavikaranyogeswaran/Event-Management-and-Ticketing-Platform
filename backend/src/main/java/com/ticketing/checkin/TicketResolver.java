package com.ticketing.checkin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ErrorCodes;
import com.ticketing.shared.security.TokenService;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketRepository;

/**
 * Finds the ticket behind a scan. The lookup is global, then checked against the event being
 * scanned, so a real ticket for another date is told apart from an unknown one (D39).
 */
@Service
class TicketResolver {

    private final TicketRepository tickets;
    private final TokenService tokenService;

    TicketResolver(TicketRepository tickets, TokenService tokenService) {
        this.tickets = tickets;
        this.tokenService = tokenService;
    }

    Ticket resolve(UUID eventId, String rawToken, String publicCode) {
        Ticket ticket = lookup(rawToken, publicCode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        CheckInErrorCodes.TICKET_NOT_FOUND, "No ticket matches that scan."));
        if (!ticket.getEventId().equals(eventId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, CheckInErrorCodes.WRONG_EVENT,
                    "That ticket is for a different event.");
        }
        return ticket;
    }

    private Optional<Ticket> lookup(String rawToken, String publicCode) {
        if (hasText(rawToken)) {
            // the raw token is hashed the same way it was stored; the token itself is never logged
            return tickets.findByValidationTokenHash(tokenService.hash(rawToken.trim()));
        }
        if (hasText(publicCode)) {
            // codes are generated uppercase, so a code typed in any case still matches
            return tickets.findByPublicCode(publicCode.trim().toUpperCase());
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
                "Scan a ticket QR or enter its code.");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
