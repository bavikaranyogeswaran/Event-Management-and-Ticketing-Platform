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
import org.springframework.transaction.support.TransactionTemplate;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCancellationTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Cancel Event";

    @Autowired
    OrderService orderService;
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

    // reserve is a modifying query, so it needs a transaction of its own here
    private int reserve(int quantity) {
        Integer rows = tx.execute(status -> ticketTypes.reserve(typeId, quantity));
        return rows == null ? 0 : rows;
    }

    private Order heldOrder(String key, int quantity, UUID owner) {
        reserve(quantity);
        Order order = new Order(UUID.randomUUID(), "ORD-2026-" + key, owner, eventId,
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("1500.00"), key, "hash");
        order.holdUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        orders.saveAndFlush(order);
        orderItems.saveAndFlush(new OrderItem(UUID.randomUUID(), order.getId(), typeId, "General",
                new BigDecimal("1500.00"), quantity, new BigDecimal("1500.00")));
        return order;
    }

    private int soldCount() {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, typeId);
    }

    private String codeOf(Throwable thrown) {
        return ((ApiException) thrown).code();
    }

    @Test
    void cancellingAHeldOrderPutsTheSeatsBackOnSale() {
        Order order = heldOrder("000001", 3, buyerId);
        assertThat(soldCount()).isEqualTo(3);

        OrderResult result = orderService.cancel(order.getId(), buyerId);

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.order().getCancelledAt()).isNotNull();
        assertThat(soldCount()).isZero();
    }

    @Test
    void cancelledSeatsCanBeBoughtBySomeoneElse() {
        Order order = heldOrder("000002", 10, buyerId);
        assertThat(reserve(1)).isZero(); // sold out

        orderService.cancel(order.getId(), buyerId);

        assertThat(reserve(10)).isEqualTo(1);
    }

    @Test
    void aConfirmedOrderCannotBeCancelled() {
        Order order = heldOrder("000003", 2, buyerId);
        order.confirm(Instant.now());
        orders.saveAndFlush(order);

        assertThatThrownBy(() -> orderService.cancel(order.getId(), buyerId))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("ORDER_NOT_CANCELLABLE"));
        assertThat(soldCount()).isEqualTo(2); // the paid seats stay claimed
    }

    @Test
    void cancellingTwiceIsRejectedAndReleasesNothingExtra() {
        Order order = heldOrder("000004", 4, buyerId);
        orderService.cancel(order.getId(), buyerId);
        assertThat(soldCount()).isZero();

        assertThatThrownBy(() -> orderService.cancel(order.getId(), buyerId))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("ORDER_NOT_CANCELLABLE"));
        assertThat(soldCount()).isZero();
    }

    @Test
    void anExpiredOrderCannotBeCancelled() {
        Order order = heldOrder("000005", 2, buyerId);
        order.expire(Instant.now());
        orders.saveAndFlush(order);

        assertThatThrownBy(() -> orderService.cancel(order.getId(), buyerId))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void anotherBuyerCannotCancelSomeoneElsesOrder() {
        Order order = heldOrder("000006", 2, buyerId);

        assertThatThrownBy(() -> orderService.cancel(order.getId(), UUID.randomUUID()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RESOURCE_NOT_FOUND"));
        assertThat(soldCount()).isEqualTo(2); // and the seats stay held for the real owner
    }

    @Test
    void cancellingAnUnknownOrderIsNotFound() {
        assertThatThrownBy(() -> orderService.cancel(UUID.randomUUID(), buyerId))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("RESOURCE_NOT_FOUND"));
    }
}
