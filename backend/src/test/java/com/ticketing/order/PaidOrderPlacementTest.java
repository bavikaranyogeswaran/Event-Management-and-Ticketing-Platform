package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** A priced order holds its seats and waits; a free one settles on the spot. */
class PaidOrderPlacementTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Paid Order Event";

    @Autowired
    OrderService orderService;
    @Autowired
    OrderItemRepository orderItems;
    @Autowired
    TicketTypeRepository ticketTypes;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    JdbcTemplate jdbc;

    private UUID buyerId;
    private UUID eventId;
    private UUID paidTypeId;
    private UUID freeTypeId;

    @BeforeEach
    void setUp() {
        clearOrders();

        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        buyerId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "buyer." + UUID.randomUUID() + "@example.com", "hash", "Buyer")).getId();
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, EVENT_TITLE_PREFIX + " " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        paidTypeId = ticketType("VIP", new BigDecimal("1500.00"));
        freeTypeId = ticketType("Free", new BigDecimal("0.00"));

        eventService.submitForReview(eventId, profile.getId(), organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());
    }

    @AfterEach
    void tearDown() {
        clearOrders();
        jdbc.update("DELETE FROM ticket_types WHERE event_id IN (SELECT id FROM events WHERE title LIKE ?)",
                EVENT_TITLE_PREFIX + "%");
        jdbc.update("DELETE FROM events WHERE title LIKE ?", EVENT_TITLE_PREFIX + "%");
    }

    private void clearOrders() {
        jdbc.update("DELETE FROM tickets");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM outbox_jobs");
    }

    private UUID ticketType(String name, BigDecimal price) {
        Instant now = Instant.now();
        return ticketTypes.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, name, null, price,
                20, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();
    }

    private OrderResult place(String key, UUID typeId, int quantity, List<String> attendees) {
        return orderService.place(buyerId, key, new OrderCommand(eventId,
                List.of(new OrderLine(typeId, quantity)), attendees));
    }

    private int soldOf(UUID typeId) {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, typeId);
    }

    @Test
    void aPaidOrderWaitsForPaymentAndIssuesNoTickets() {
        OrderResult result = place("paid-1", paidTypeId, 2, List.of("Asha", "Nuwan"));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.order().getConfirmedAt()).isNull();
        assertThat(result.order().getGrandTotal()).isEqualByComparingTo("3000.00");
        assertThat(result.tickets()).isEmpty();
    }

    @Test
    void aPaidOrderStillClaimsItsSeatsImmediately() {
        place("paid-2", paidTypeId, 3, List.of("A", "B", "C"));

        assertThat(soldOf(paidTypeId)).isEqualTo(3);
    }

    @Test
    void aPaidOrderCarriesADeadline() {
        OrderResult result = place("paid-3", paidTypeId, 1, List.of("Asha"));

        assertThat(result.order().getExpiresAt())
                .isNotNull()
                .isAfter(Instant.now())
                .isBefore(Instant.now().plus(20, ChronoUnit.MINUTES));
    }

    @Test
    void attendeeNamesAreKeptSoTicketsCanBeIssuedLater() {
        OrderResult result = place("paid-4", paidTypeId, 2, List.of("Asha Perera", "Nuwan Silva"));

        assertThat(orderItems.findByOrderIdOrderByCreatedAtAsc(result.order().getId()))
                .singleElement()
                .satisfies(item -> assertThat(item.getAttendeeNames())
                        .containsExactly("Asha Perera", "Nuwan Silva"));
    }

    @Test
    void noConfirmationEmailIsQueuedBeforePayment() {
        OrderResult result = place("paid-5", paidTypeId, 1, List.of("Asha"));

        Integer queued = jdbc.queryForObject("SELECT count(*) FROM outbox_jobs WHERE job_key = ?",
                Integer.class, "ORDER_CONFIRMATION:" + result.order().getId());
        assertThat(queued).isZero();
    }

    @Test
    void aFreeOrderStillConfirmsOnTheSpot() {
        OrderResult result = place("free-1", freeTypeId, 2, List.of("Asha", "Nuwan"));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.order().getExpiresAt()).isNull();
        assertThat(result.tickets()).hasSize(2);
        assertThat(soldOf(freeTypeId)).isEqualTo(2);
    }

    @Test
    void anUnpaidOrderCanBeCancelledToFreeItsSeats() {
        OrderResult placed = place("paid-6", paidTypeId, 4, List.of("A", "B", "C", "D"));
        assertThat(soldOf(paidTypeId)).isEqualTo(4);

        orderService.cancel(placed.order().getId(), buyerId);

        assertThat(soldOf(paidTypeId)).isZero();
    }
}
