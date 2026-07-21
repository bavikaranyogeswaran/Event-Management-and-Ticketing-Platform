package com.ticketing.checkin;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.Event;
import com.ticketing.shared.security.CurrentUser;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketStatus;
import com.ticketing.tickettype.TicketTypeRepository;

@Service
public class CheckInService {

    private final CheckInAuthority authority;
    private final TicketResolver resolver;
    private final CheckInRecorder recorder;
    private final CheckInRepository checkIns;
    private final TicketTypeRepository ticketTypes;

    CheckInService(CheckInAuthority authority, TicketResolver resolver, CheckInRecorder recorder,
            CheckInRepository checkIns, TicketTypeRepository ticketTypes) {
        this.authority = authority;
        this.resolver = resolver;
        this.recorder = recorder;
        this.checkIns = checkIns;
        this.ticketTypes = ticketTypes;
    }

    /**
     * Admits a ticket exactly once. Not transactional itself: when two scans race, the loser's
     * transaction must roll back before this can read the winner's check-in time.
     */
    public CheckInReceipt checkIn(CheckInCommand command, CurrentUser user) {
        Event event = authority.requireCanCheckIn(command.eventId(), user);
        Ticket ticket = resolver.resolve(command.eventId(), command.token(), command.publicCode());
        CheckInMethod method = scannedByQr(command) ? CheckInMethod.QR : CheckInMethod.MANUAL;
        try {
            return recorder.record(ticket.getId(), event.getId(), user.userId(), method);
        } catch (DataIntegrityViolationException raced) {
            // a parallel scan committed first; answer with its original time instead of an error
            Instant when = checkIns.findByTicketId(ticket.getId())
                    .map(CheckIn::getCheckedInAt)
                    .orElseThrow(() -> raced);
            throw CheckInErrors.alreadyCheckedIn(when);
        }
    }

    private boolean scannedByQr(CheckInCommand command) {
        return command.token() != null && !command.token().isBlank();
    }

    /**
     * A pure read: says whose ticket it is and whether it would be admitted, and consumes nothing.
     * An unresolvable scan or an unauthorised scanner still fails; a used or cancelled ticket is
     * described rather than refused, so staff can see what is wrong before deciding.
     */
    @Transactional(readOnly = true)
    public CheckInView validate(CheckInCommand command, CurrentUser user) {
        authority.requireCanCheckIn(command.eventId(), user);
        Ticket ticket = resolver.resolve(command.eventId(), command.token(), command.publicCode());
        return describe(ticket);
    }

    private CheckInView describe(Ticket ticket) {
        Instant checkedInAt = checkIns.findByTicketId(ticket.getId())
                .map(CheckIn::getCheckedInAt)
                .orElse(null);
        boolean allowed = ticket.getStatus() == TicketStatus.VALID && checkedInAt == null;
        String ticketTypeName = ticketTypes.findById(ticket.getTicketTypeId())
                .map(type -> type.getName())
                .orElse(null);
        return new CheckInView(ticket.getId(), ticket.getAttendeeName(), ticketTypeName,
                ticket.getStatus().name(), allowed, checkedInAt);
    }
}
