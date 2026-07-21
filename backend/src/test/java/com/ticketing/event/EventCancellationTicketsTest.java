package com.ticketing.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
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

@Transactional
class EventCancellationTicketsTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    EventService eventService;
    @Autowired
    OrderService orderService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    jakarta.persistence.EntityManager entityManager;

    private UUID organizerUserId;
    private UUID organizerId;
    private UUID buyerOne;
    private UUID buyerTwo;
    private UUID eventId;
    private UUID typeId;

    @BeforeEach
    void setUp() {
        User organizerUser = user("org");
        organizerUserId = organizerUser.getId();
        organizerId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();
        buyerOne = user("buyer1").getId();
        buyerTwo = user("buyer2").getId();

        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "Cancellable Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        typeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();
        eventService.submitForReview(eventId, organizerId, organizerUserId);
        eventService.approve(eventId, organizerUserId);
    }

    private User user(String label) {
        return userRepository.saveAndFlush(
                new User(UUID.randomUUID(), label + "." + UUID.randomUUID() + "@example.com", "hash", label));
    }

    private void order(UUID buyer, String key, int quantity, List<String> attendees) {
        orderService.place(buyer, key, new OrderCommand(eventId, List.of(new OrderLine(typeId, quantity)), attendees));
    }

    // flush so the event update and outbox rows reach the connection the jdbc reads use
    private void cancel() {
        eventService.cancelByOrganizer(eventId, organizerId, organizerUserId);
        entityManager.flush();
    }

    private long ticketsWithStatus(String status) {
        return jdbc.queryForObject("SELECT count(*) FROM tickets WHERE event_id = ? AND status = ?",
                Long.class, eventId, status);
    }

    private long cancellationJobs() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_jobs WHERE job_key LIKE ?",
                Long.class, "EVENT_CANCELLATION:" + eventId + ":%");
    }

    @Test
    void cancellingAnEventVoidsItsValidTickets() {
        order(buyerOne, "c1", 2, List.of("Asha", "Nuwan"));

        cancel();

        assertThat(ticketsWithStatus("CANCELLED")).isEqualTo(2);
        assertThat(ticketsWithStatus("VALID")).isZero();
    }

    @Test
    void eachHolderIsNotifiedOnce() {
        order(buyerOne, "c2", 2, List.of("Asha", "Nuwan")); // two tickets, one holder
        order(buyerTwo, "c3", 1, List.of("Kamal"));

        cancel();

        // one notice per holder, not per ticket
        assertThat(cancellationJobs()).isEqualTo(2);
    }

    @Test
    void anAlreadyUsedTicketIsLeftAsAttendedHistory() {
        order(buyerOne, "c4", 1, List.of("Asha"));
        UUID ticketId = jdbc.queryForObject("SELECT id FROM tickets WHERE event_id = ? LIMIT 1",
                UUID.class, eventId);
        jdbc.update("UPDATE tickets SET status = 'USED' WHERE id = ?", ticketId);

        cancel();

        // the used ticket records a real admission and must not be rewritten to CANCELLED
        assertThat(ticketsWithStatus("USED")).isEqualTo(1);
        assertThat(ticketsWithStatus("CANCELLED")).isZero();
    }

    @Test
    void cancellingAnEventWithNoTicketsNotifiesNobody() {
        cancel();

        assertThat(cancellationJobs()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM events WHERE id = ?", String.class, eventId))
                .isEqualTo("CANCELLED");
    }

    @Test
    void everythingCommitsTogether() {
        order(buyerOne, "c5", 1, List.of("Asha"));

        cancel();

        // event cancelled, ticket cancelled, holder notified: one atomic outcome
        assertThat(jdbc.queryForObject("SELECT status FROM events WHERE id = ?", String.class, eventId))
                .isEqualTo("CANCELLED");
        assertThat(ticketsWithStatus("CANCELLED")).isEqualTo(1);
        assertThat(cancellationJobs()).isEqualTo(1);
    }
}
