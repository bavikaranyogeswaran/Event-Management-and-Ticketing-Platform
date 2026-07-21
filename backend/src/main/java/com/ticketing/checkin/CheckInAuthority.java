package com.ticketing.checkin;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.Event;
import com.ticketing.event.EventRepository;
import com.ticketing.event.EventStaffAssignmentRepository;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.security.CurrentUser;
import com.ticketing.shared.security.Role;

/**
 * Answers, per event, whether a person may check tickets in there. Authority is never global:
 * staff reach only assigned events, organizers only owned ones, admins any (D38).
 */
@Service
class CheckInAuthority {

    private final EventRepository events;
    private final EventStaffAssignmentRepository assignments;
    private final OrganizerProfileRepository organizerProfiles;

    CheckInAuthority(EventRepository events, EventStaffAssignmentRepository assignments,
            OrganizerProfileRepository organizerProfiles) {
        this.events = events;
        this.assignments = assignments;
        this.organizerProfiles = organizerProfiles;
    }

    /** The event the scanner is cleared to work, or a refusal. */
    @Transactional(readOnly = true)
    Event requireCanCheckIn(UUID eventId, CurrentUser user) {
        Event event = events.findById(eventId).orElseThrow(ResourceNotFoundException::new);
        if (mayCheckIn(event, user)) {
            return event;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, CheckInErrorCodes.NOT_ASSIGNED_TO_EVENT,
                "You are not assigned to check in tickets for this event.");
    }

    private boolean mayCheckIn(Event event, CurrentUser user) {
        if (user.hasRole(Role.ADMIN)) {
            return true;
        }
        if (user.hasRole(Role.STAFF) && assignments.existsByEventIdAndUserId(event.getId(), user.userId())) {
            return true;
        }
        return user.hasRole(Role.ORGANIZER) && ownsEvent(event, user.userId());
    }

    // the event's organizerId is a profile id, so ownership is the profile that belongs to this user
    private boolean ownsEvent(Event event, UUID userId) {
        return organizerProfiles.findByUserId(userId)
                .filter(profile -> profile.getId().equals(event.getOrganizerId()))
                .isPresent();
    }
}
