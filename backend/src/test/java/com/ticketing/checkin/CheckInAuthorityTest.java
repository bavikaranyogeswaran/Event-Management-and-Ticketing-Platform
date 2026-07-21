package com.ticketing.checkin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventStaffAssignment;
import com.ticketing.event.EventStaffAssignmentRepository;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.security.CurrentUser;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CheckInAuthorityTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    CheckInAuthority authority;
    @Autowired
    EventService eventService;
    @Autowired
    EventStaffAssignmentRepository assignments;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID ownerUserId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        User organizerUser = user("owner");
        organizerUser.addRole(Role.ORGANIZER);
        userRepository.saveAndFlush(organizerUser);
        ownerUserId = organizerUser.getId();
        UUID organizerId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), ownerUserId, "Org", null, null)).getId();
        eventId = newEvent(organizerId);
    }

    private User user(String label) {
        return userRepository.saveAndFlush(
                new User(UUID.randomUUID(), label + "." + UUID.randomUUID() + "@example.com", "hash", label));
    }

    private UUID newEvent(UUID organizerId) {
        Instant now = Instant.now();
        return eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "Door " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    private CurrentUser principal(UUID userId, Role... roles) {
        return new CurrentUser(userId, "u@example.com", Set.of(roles), true);
    }

    private String codeOf(Throwable thrown) {
        return ((ApiException) thrown).code();
    }

    @Test
    void anAdminMayCheckInAnyEvent() {
        assertThat(authority.requireCanCheckIn(eventId, principal(UUID.randomUUID(), Role.ADMIN)).getId())
                .isEqualTo(eventId);
    }

    @Test
    void theOwningOrganizerMayCheckIn() {
        assertThat(authority.requireCanCheckIn(eventId, principal(ownerUserId, Role.ORGANIZER)).getId())
                .isEqualTo(eventId);
    }

    @Test
    void aDifferentOrganizerMayNot() {
        User other = user("other");
        other.addRole(Role.ORGANIZER);
        userRepository.saveAndFlush(other);
        organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), other.getId(), "Other Org", null, null));

        assertThatThrownBy(() -> authority.requireCanCheckIn(eventId, principal(other.getId(), Role.ORGANIZER)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("NOT_ASSIGNED_TO_EVENT"));
    }

    @Test
    void assignedStaffMayCheckIn() {
        UUID staffId = user("staff").getId();
        assignments.saveAndFlush(new EventStaffAssignment(UUID.randomUUID(), eventId, staffId, ownerUserId));

        assertThat(authority.requireCanCheckIn(eventId, principal(staffId, Role.STAFF)).getId())
                .isEqualTo(eventId);
    }

    @Test
    void staffAssignedToAnotherEventMayNot() {
        UUID staffId = user("staff").getId();
        UUID otherEventId = newEvent(organizerProfileRepository.findByUserId(ownerUserId).orElseThrow().getId());
        assignments.saveAndFlush(new EventStaffAssignment(UUID.randomUUID(), otherEventId, staffId, ownerUserId));

        assertThatThrownBy(() -> authority.requireCanCheckIn(eventId, principal(staffId, Role.STAFF)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("NOT_ASSIGNED_TO_EVENT"));
    }

    @Test
    void aStaffRoleWithoutAnAssignmentMayNot() {
        UUID staffId = user("staff").getId();

        assertThatThrownBy(() -> authority.requireCanCheckIn(eventId, principal(staffId, Role.STAFF)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("NOT_ASSIGNED_TO_EVENT"));
    }

    @Test
    void aPlainAttendeeMayNot() {
        assertThatThrownBy(() -> authority.requireCanCheckIn(eventId, principal(UUID.randomUUID(), Role.ATTENDEE)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("NOT_ASSIGNED_TO_EVENT"));
    }

    @Test
    void anUnknownEventIsNotFound() {
        assertThatThrownBy(() -> authority.requireCanCheckIn(UUID.randomUUID(), principal(ownerUserId, Role.ADMIN)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RESOURCE_NOT_FOUND"));
    }
}
