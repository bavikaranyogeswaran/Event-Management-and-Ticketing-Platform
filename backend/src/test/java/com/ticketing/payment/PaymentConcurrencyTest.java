package com.ticketing.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.order.OrderCommand;
import com.ticketing.order.OrderLine;
import com.ticketing.order.OrderRepository;
import com.ticketing.order.OrderService;
import com.ticketing.order.OrderStatus;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.ticket.TicketRepository;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves one payment settles an order exactly once, however many deliveries arrive together. */
class PaymentConcurrencyTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Race Event";
    private static final int THREADS = 12;

    @Autowired
    PaymentWebhookService webhook;
    @Autowired
    PaymentRepository payments;
    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orders;
    @Autowired
    TicketRepository tickets;
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

        paidTypeId = ticketTypes.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "VIP", null,
                new BigDecimal("1500.00"), 50, 4,
                now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();

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
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM outbox_jobs");
    }

    private UUID placePaidOrder(String key) {
        return orderService.place(buyerId, key, new OrderCommand(eventId,
                List.of(new OrderLine(paidTypeId, 2)), List.of("Asha", "Nuwan"))).order().getId();
    }

    private PaymentEvent paidEvent(UUID orderId, String paymentId) {
        return new PaymentEvent("evt_" + paymentId, PaymentEventType.PAYMENT_SUCCEEDED,
                orderId, paymentId, 300_000L, "LKR", null);
    }

    private OrderStatus statusOf(UUID orderId) {
        return orders.findById(orderId).orElseThrow().getStatus();
    }

    private int soldCount() {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, paidTypeId);
    }

    private int confirmationsQueued(UUID orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_jobs WHERE job_key = ?",
                Integer.class, "ORDER_CONFIRMATION:" + orderId);
    }

    /** Releases every task at once so the work genuinely overlaps. */
    private void runTogether(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (Runnable task : tasks) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    task.run();
                    return null;
                }));
            }
            startGun.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelDeliveriesOfOnePaymentSettleItExactlyOnce() throws Exception {
        UUID orderId = placePaidOrder("race-1");
        PaymentEvent event = paidEvent(orderId, "pi_race_1");

        List<Runnable> deliveries = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            deliveries.add(() -> webhook.handle(PaymentProvider.STRIPE, event));
        }
        runTogether(deliveries);

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(payments.findByOrderIdOrderByCreatedAtAsc(orderId)).hasSize(1);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(2);
        assertThat(confirmationsQueued(orderId)).isEqualTo(1);
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    void aSecondChargeForTheSameOrderNeverDoublesTheTickets() throws Exception {
        UUID orderId = placePaidOrder("race-2");

        // two genuinely different payments landing together, as a double charge would look
        runTogether(List.of(
                () -> webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_race_2a")),
                () -> webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_race_2b"))));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(payments.findByOrderIdOrderByCreatedAtAsc(orderId)).hasSize(2); // both are on record
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(2); // but one set of tickets
        assertThat(confirmationsQueued(orderId)).isEqualTo(1);
        assertThat(soldCount()).isEqualTo(2);
    }

}
