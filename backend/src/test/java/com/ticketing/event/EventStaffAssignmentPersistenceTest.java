package com.ticketing.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class EventStaffAssignmentPersistenceTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    EventStaffAssignmentRepository assignments;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID organizerUserId;
    private UUID organizerId;
    private UUID staffUserId;
    private UUID eventId;
    private UUID otherEventId;

    @BeforeEach
    void setUp() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        organizerUserId = organizerUser.getId();
        staffUserId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "staff." + UUID.randomUUID() + "@example.com", "hash", "Staff")).getId();
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Test Org", null, null));
        organizerId = profile.getId();

        eventId = newEvent("Door Event");
        otherEventId = newEvent("Other Event");
    }

    private UUID newEvent(String title) {
        Instant now = Instant.now();
        return eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, title + " " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    private EventStaffAssignment assign(UUID event, UUID user) {
        return assignments.saveAndFlush(
                new EventStaffAssignment(UUID.randomUUID(), event, user, organizerUserId));
    }

    @Test
    void anAssignmentRecordsWhoPutThePersonOnTheDoor() {
        EventStaffAssignment saved = assign(eventId, staffUserId);

        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getUserId()).isEqualTo(staffUserId);
        assertThat(saved.getAssignedBy()).isEqualTo(organizerUserId);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void theSamePersonCannotBeAssignedTwiceToOneEvent() {
        assign(eventId, staffUserId);

        assertThatThrownBy(() -> assign(eventId, staffUserId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAssignmentGrantsNothingAtAnotherEvent() {
        assign(eventId, staffUserId);

        assertThat(assignments.existsByEventIdAndUserId(eventId, staffUserId)).isTrue();
        assertThat(assignments.existsByEventIdAndUserId(otherEventId, staffUserId)).isFalse();
    }

    @Test
    void anEventListsItsOwnStaffOnly() {
        assign(eventId, staffUserId);
        assign(otherEventId, staffUserId);

        assertThat(assignments.findByEventIdOrderByCreatedAtAsc(eventId))
                .singleElement()
                .extracting(EventStaffAssignment::getUserId)
                .isEqualTo(staffUserId);
    }

    @Test
    void countingAcrossEventsDecidesWhetherTheRoleStillMeansAnything() {
        assertThat(assignments.countByUserId(staffUserId)).isZero();

        assign(eventId, staffUserId);
        assign(otherEventId, staffUserId);
        assertThat(assignments.countByUserId(staffUserId)).isEqualTo(2);

        assignments.delete(assignments.findByEventIdAndUserId(eventId, staffUserId).orElseThrow());
        assignments.flush();
        // still one door left, so the role is still worth holding
        assertThat(assignments.countByUserId(staffUserId)).isEqualTo(1);
    }

    @Test
    void anAssignmentCanBeLookedUpAndRemoved() {
        assign(eventId, staffUserId);
        assertThat(assignments.findByEventIdAndUserId(eventId, staffUserId)).isPresent();

        assignments.delete(assignments.findByEventIdAndUserId(eventId, staffUserId).orElseThrow());
        assignments.flush();

        assertThat(assignments.findByEventIdAndUserId(eventId, staffUserId)).isEmpty();
        assertThat(assignments.existsByEventIdAndUserId(eventId, staffUserId)).isFalse();
    }
}
