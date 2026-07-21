package com.ticketing.checkin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.order.OrderCommand;
import com.ticketing.order.OrderLine;
import com.ticketing.order.OrderService;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CheckInPersistenceTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    CheckInRepository checkIns;
    @Autowired
    OrderService orderService;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;

    private UUID staffUserId;
    private UUID eventId;
    private UUID ticketId;

    @BeforeEach
    void setUp() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        UUID buyerId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "buyer." + UUID.randomUUID() + "@example.com", "hash", "Buyer")).getId();
        staffUserId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "staff." + UUID.randomUUID() + "@example.com", "hash", "Staff")).getId();
        UUID organizerId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Org", null, null)).getId();

        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "CheckIn Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS)));
        eventService.submitForReview(eventId, organizerId, organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());

        UUID typeId = ticketTypeRepository.findByEventIdOrderByCreatedAtAsc(eventId).get(0).getId();
        ticketId = orderService.place(buyerId, "ci-key",
                new OrderCommand(eventId, List.of(new OrderLine(typeId, 1)), List.of("Asha")))
                .tickets().get(0).getId();
    }

    private CheckIn record(UUID ticket, CheckInMethod method) {
        return checkIns.saveAndFlush(new CheckIn(UUID.randomUUID(), ticket, eventId, staffUserId, method));
    }

    @Test
    void aCheckInRecordsWhoAdmittedTheTicketAndWhen() {
        CheckIn saved = record(ticketId, CheckInMethod.QR);

        assertThat(saved.getTicketId()).isEqualTo(ticketId);
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getStaffUserId()).isEqualTo(staffUserId);
        assertThat(saved.getMethod()).isEqualTo(CheckInMethod.QR);
        assertThat(saved.getCheckedInAt()).isNotNull();
    }

    @Test
    void aTicketCanOnlyBeCheckedInOnce() {
        record(ticketId, CheckInMethod.QR);

        // the last-line defence against admitting one ticket twice
        assertThatThrownBy(() -> record(ticketId, CheckInMethod.MANUAL))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theFirstCheckInCanBeReadBackForItsTicket() {
        CheckIn saved = record(ticketId, CheckInMethod.QR);

        assertThat(checkIns.findByTicketId(ticketId))
                .get()
                .satisfies(found -> {
                    assertThat(found.getId()).isEqualTo(saved.getId());
                    assertThat(found.getCheckedInAt()).isEqualTo(saved.getCheckedInAt());
                });
    }

    @Test
    void anUncheckedTicketHasNoRecord() {
        assertThat(checkIns.findByTicketId(ticketId)).isEmpty();
    }

    @Test
    void attendanceIsCountedPerEvent() {
        assertThat(checkIns.countByEventId(eventId)).isZero();
        record(ticketId, CheckInMethod.QR);
        assertThat(checkIns.countByEventId(eventId)).isEqualTo(1);
    }
}
