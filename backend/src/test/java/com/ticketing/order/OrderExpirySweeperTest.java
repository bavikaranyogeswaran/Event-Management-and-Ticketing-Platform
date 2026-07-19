package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs without a wrapping transaction: the sweep opens its own per-order transactions. */
class OrderExpirySweeperTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Sweep Event";

    @Autowired
    OrderExpirySweeper sweeper;
    @Autowired
    OrderRepository orders;
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
    @Autowired
    TransactionTemplate tx;

    private UUID buyerId;
    private UUID eventId;
    private UUID typeId;

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

        typeId = ticketTypes.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("1500.00"), 10, 4,
                now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();
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

    // the reserve and release statements are modifying queries, so they need a transaction of their own
    private int reserve(int quantity) {
        return tx.execute(status -> ticketTypes.reserve(typeId, quantity));
    }

    /** Mirrors what paid checkout will do: claim the seats, then hold the order against a clock. */
    private Order heldOrder(String key, int quantity, Instant expiresAt) {
        reserve(quantity);
        Order order = new Order(UUID.randomUUID(), "ORD-2026-" + key, buyerId, eventId,
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("1500.00"), key, "hash");
        order.holdUntil(expiresAt);
        orders.saveAndFlush(order);
        orderItems.saveAndFlush(new OrderItem(UUID.randomUUID(), order.getId(), typeId, "General",
                new BigDecimal("1500.00"), quantity, new BigDecimal("1500.00")));
        return order;
    }

    private int soldCount() {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, typeId);
    }

    private OrderStatus statusOf(UUID orderId) {
        return orders.findById(orderId).orElseThrow().getStatus();
    }

    @Test
    void anOverdueHoldGivesBackItsSeats() {
        Order order = heldOrder("000001", 3, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThat(soldCount()).isEqualTo(3);

        assertThat(sweeper.sweepOnce()).isEqualTo(1);

        assertThat(statusOf(order.getId())).isEqualTo(OrderStatus.EXPIRED);
        assertThat(soldCount()).isZero();
    }

    @Test
    void aHoldInsideItsWindowIsLeftAlone() {
        Order order = heldOrder("000002", 2, Instant.now().plus(10, ChronoUnit.MINUTES));

        assertThat(sweeper.sweepOnce()).isZero();

        assertThat(statusOf(order.getId())).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    void anOrderPaidJustBeforeTheSweepKeepsItsSeats() {
        // the race that matters: payment landed while the order was already overdue
        Order order = heldOrder("000003", 2, Instant.now().minus(1, ChronoUnit.MINUTES));
        order.confirm(Instant.now());
        orders.saveAndFlush(order);

        assertThat(sweeper.sweepOnce()).isZero();

        assertThat(statusOf(order.getId())).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    void sweepingAgainReleasesNothingExtra() {
        heldOrder("000004", 4, Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThat(sweeper.sweepOnce()).isEqualTo(1);
        assertThat(soldCount()).isZero();

        assertThat(sweeper.sweepOnce()).isZero();
        assertThat(soldCount()).isZero();
    }

    @Test
    void severalOverdueHoldsAreAllReturned() {
        heldOrder("000005", 2, Instant.now().minus(5, ChronoUnit.MINUTES));
        heldOrder("000006", 3, Instant.now().minus(2, ChronoUnit.MINUTES));
        assertThat(soldCount()).isEqualTo(5);

        assertThat(sweeper.sweepOnce()).isEqualTo(2);
        assertThat(soldCount()).isZero();
    }

    @Test
    void releasedSeatsBecomeSellableAgain() {
        heldOrder("000007", 10, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThat(reserve(1)).isZero(); // sold out while held

        sweeper.sweepOnce();

        assertThat(reserve(10)).isEqualTo(1);
    }
}
