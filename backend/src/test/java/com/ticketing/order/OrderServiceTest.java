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
import com.ticketing.shared.api.ApiException;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketStatus;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs without a surrounding test transaction: the rollback and replay behaviour under test
 * only shows up when each order really commits.
 */
class OrderServiceTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Order Test Event";

    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;
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

        freeTypeId = ticketType("General", 10, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS));

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

    private UUID ticketType(String name, int quantityTotal, Instant salesStart, Instant salesEnd) {
        TicketType type = new TicketType(UUID.randomUUID(), eventId, name, null, new BigDecimal("0.00"),
                quantityTotal, 4, salesStart, salesEnd);
        return ticketTypeRepository.saveAndFlush(type).getId();
    }

    private OrderCommand order(int quantity, List<String> attendees) {
        return new OrderCommand(eventId, List.of(new OrderLine(freeTypeId, quantity)), attendees);
    }

    private int soldCount() {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, freeTypeId);
    }

    @Test
    void freeOrderConfirmsAndIssuesOneTicketPerAttendee() {
        OrderResult result = orderService.place(buyerId, "key-1", order(2, List.of("Asha", "Nuwan")));

        assertThat(result.replay()).isFalse();
        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.order().getConfirmedAt()).isNotNull();
        assertThat(result.order().getGrandTotal()).isEqualByComparingTo("0.00");
        assertThat(result.order().getOrderNumber()).matches("ORD-\\d{4}-\\d{6}");
        assertThat(result.items()).hasSize(1);
        assertThat(result.tickets()).hasSize(2)
                .allSatisfy(ticket -> assertThat(ticket.getStatus()).isEqualTo(TicketStatus.VALID));
        assertThat(result.tickets()).extracting(Ticket::getAttendeeName).containsExactly("Asha", "Nuwan");
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    void ticketsCarryDistinctCodesAndHashes() {
        OrderResult result = orderService.place(buyerId, "key-2", order(3, List.of("A", "B", "C")));

        assertThat(result.tickets()).extracting(Ticket::getPublicCode).doesNotHaveDuplicates();
        assertThat(result.tickets()).extracting(Ticket::getValidationTokenHash).doesNotHaveDuplicates();
    }

    @Test
    void orderItemSnapshotsNameAndPrice() {
        OrderResult result = orderService.place(buyerId, "key-3", order(1, List.of("Asha")));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.getTicketTypeName()).isEqualTo("General");
            assertThat(item.getUnitPrice()).isEqualByComparingTo("0.00");
            assertThat(item.getQuantity()).isEqualTo(1);
        });
    }

    @Test
    void replayWithTheSameKeyReturnsTheOriginalOrder() {
        OrderResult first = orderService.place(buyerId, "repeat", order(2, List.of("Asha", "Nuwan")));
        OrderResult second = orderService.place(buyerId, "repeat", order(2, List.of("Asha", "Nuwan")));

        assertThat(second.replay()).isTrue();
        assertThat(second.order().getId()).isEqualTo(first.order().getId());
        assertThat(second.tickets()).hasSize(2);
        assertThat(soldCount()).isEqualTo(2); // a replay must not claim more stock
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void sameKeyWithADifferentBasketIsRejected() {
        orderService.place(buyerId, "clash", order(1, List.of("Asha")));

        assertThatThrownBy(() -> orderService.place(buyerId, "clash", order(2, List.of("Asha", "Nuwan"))))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void orderBeyondRemainingStockIsRejectedAndClaimsNothing() {
        orderService.place(buyerId, "fill-1", order(4, List.of("A", "B", "C", "D")));
        orderService.place(buyerId, "fill-2", order(4, List.of("E", "F", "G", "H")));
        assertThat(soldCount()).isEqualTo(8);

        assertThatThrownBy(() -> orderService.place(buyerId, "overflow", order(3, List.of("I", "J", "K"))))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("TICKET_INVENTORY_EXHAUSTED"));

        assertThat(soldCount()).isEqualTo(8);
    }

    @Test
    void failedOrderRollsBackTheInventoryClaim() {
        // the second line is past its sales window, so it fails after the first line was already claimed
        Instant now = Instant.now();
        UUID closedTypeId = ticketType("Closed", 50,
                now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        OrderCommand mixed = new OrderCommand(eventId,
                List.of(new OrderLine(freeTypeId, 2), new OrderLine(closedTypeId, 1)),
                List.of("Asha", "Nuwan", "Kamal"));

        assertThatThrownBy(() -> orderService.place(buyerId, "mixed", mixed))
                .isInstanceOf(ApiException.class);

        assertThat(soldCount()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void ownerCanReadTheirOrderAndOthersCannot() {
        OrderResult placed = orderService.place(buyerId, "read", order(1, List.of("Asha")));

        OrderResult fetched = orderService.getOwnedOrder(placed.order().getId(), buyerId);
        assertThat(fetched.order().getId()).isEqualTo(placed.order().getId());
        assertThat(fetched.tickets()).hasSize(1);

        assertThatThrownBy(() -> orderService.getOwnedOrder(placed.order().getId(), UUID.randomUUID()))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("RESOURCE_NOT_FOUND"));
    }

    @Test
    void confirmationJobIsQueuedForTheOrder() {
        OrderResult result = orderService.place(buyerId, "job", order(1, List.of("Asha")));

        Integer queued = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_jobs WHERE job_key = ?", Integer.class,
                "ORDER_CONFIRMATION:" + result.order().getId());
        assertThat(queued).isEqualTo(1);
    }
}
