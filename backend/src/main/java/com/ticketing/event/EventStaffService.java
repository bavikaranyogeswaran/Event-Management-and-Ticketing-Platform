package com.ticketing.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.audit.AuditActions;
import com.ticketing.audit.AuditService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.port.IdGenerator;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

/** Who an organizer has put on the door for one of their events. */
@Service
public class EventStaffService {

    private final EventStaffAssignmentRepository assignments;
    private final EventService events;
    private final UserRepository users;
    private final IdGenerator idGenerator;
    private final AuditService audit;

    EventStaffService(EventStaffAssignmentRepository assignments, EventService events,
            UserRepository users, IdGenerator idGenerator, AuditService audit) {
        this.assignments = assignments;
        this.events = events;
        this.users = users;
        this.idGenerator = idGenerator;
        this.audit = audit;
    }

    @Transactional
    public EventStaffMember assign(UUID eventId, UUID organizerId, UUID actingUserId, String email) {
        events.getOwnedEvent(eventId, organizerId); // ownership first: nothing leaks about other events
        User staff = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, EventErrorCodes.STAFF_USER_NOT_FOUND,
                        "No account exists for that email address."));
        if (assignments.existsByEventIdAndUserId(eventId, staff.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, EventErrorCodes.STAFF_ALREADY_ASSIGNED,
                    "That person is already on this event's staff.");
        }

        // the role is what shows them the check-in screen; the assignment is what authorises the door
        staff.addRole(Role.STAFF);
        users.save(staff);
        EventStaffAssignment assignment = assignments.save(
                new EventStaffAssignment(idGenerator.newId(), eventId, staff.getId(), actingUserId));

        audit.record(AuditActions.EVENT_STAFF_ASSIGNED, actingUserId, "EVENT", eventId,
                Map.of("staffUserId", staff.getId().toString()));
        return toMember(assignment, staff);
    }

    @Transactional
    public void remove(UUID eventId, UUID organizerId, UUID actingUserId, UUID staffUserId) {
        events.getOwnedEvent(eventId, organizerId);
        EventStaffAssignment assignment = assignments.findByEventIdAndUserId(eventId, staffUserId)
                .orElseThrow(ResourceNotFoundException::new);
        assignments.delete(assignment);
        assignments.flush(); // the count below has to see this removal

        // a role that no longer opens any door should not linger on the account
        if (assignments.countByUserId(staffUserId) == 0) {
            users.findById(staffUserId).ifPresent(staff -> {
                staff.removeRole(Role.STAFF);
                users.save(staff);
            });
        }

        audit.record(AuditActions.EVENT_STAFF_REMOVED, actingUserId, "EVENT", eventId,
                Map.of("staffUserId", staffUserId.toString()));
    }

    @Transactional(readOnly = true)
    public List<EventStaffMember> list(UUID eventId, UUID organizerId) {
        events.getOwnedEvent(eventId, organizerId);
        List<EventStaffAssignment> rows = assignments.findByEventIdOrderByCreatedAtAsc(eventId);
        // one lookup for the whole list rather than one per assignment
        Map<UUID, User> byId = users.findAllById(rows.stream().map(EventStaffAssignment::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream()
                .map(row -> toMember(row, byId.get(row.getUserId())))
                .toList();
    }

    private EventStaffMember toMember(EventStaffAssignment assignment, User staff) {
        return new EventStaffMember(assignment.getUserId(),
                staff == null ? null : staff.getDisplayName(),
                staff == null ? null : staff.getEmail(),
                assignment.getCreatedAt());
    }
}
