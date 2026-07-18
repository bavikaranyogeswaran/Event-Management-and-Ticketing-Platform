package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.ticket.TicketRepository;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the correctness invariants under real parallel load: stock is never oversold,
 * and one idempotency key can only ever produce one order.
 */
class OrderConcurrencyTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Concurrency Event";
    private static final int THREADS = 16;
    private static final int CAPACITY = 5;

    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    TicketRepository ticketRepository;
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

        freeTypeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), CAPACITY, 4,
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
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM outbox_jobs");
    }

    private OrderCommand oneTicketFor(String attendee) {
        return new OrderCommand(eventId, List.of(new OrderLine(freeTypeId, 1)), List.of(attendee));
    }

    private int soldCount() {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, freeTypeId);
    }

    /** Releases every thread at once, so the requests genuinely overlap instead of queueing. */
    private List<String> runInParallel(java.util.function.IntFunction<String> attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    startGun.await();
                    return attempt.apply(index);
                }));
            }
            startGun.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelPurchasesNeverOversell() throws Exception {
        List<String> outcomes = runInParallel(index -> {
            try {
                orderService.place(buyerId, "race-" + index, oneTicketFor("Guest " + index));
                return "CONFIRMED";
            } catch (ApiException e) {
                return e.code();
            }
        });

        long confirmed = outcomes.stream().filter("CONFIRMED"::equals).count();
        assertThat(confirmed).isEqualTo(CAPACITY);
        // everyone who missed out was told why, rather than failing some other way
        assertThat(outcomes).filteredOn(outcome -> !"CONFIRMED".equals(outcome))
                .containsOnly("TICKET_INVENTORY_EXHAUSTED");

        assertThat(soldCount()).isEqualTo(CAPACITY);
        assertThat(orderRepository.count()).isEqualTo(CAPACITY);
        assertThat(ticketRepository.count()).isEqualTo(CAPACITY);
    }

    @Test
    void parallelRequestsSharingOneKeyProduceOneOrder() throws Exception {
        List<String> outcomes = runInParallel(index -> {
            try {
                return orderService.place(buyerId, "one-key", oneTicketFor("Asha")).order().getId().toString();
            } catch (ApiException e) {
                return "FAILED:" + e.code();
            }
        });

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).doesNotStartWith("FAILED:"));
        // every caller was handed the same order
        assertThat(Set.copyOf(outcomes)).hasSize(1);

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(ticketRepository.count()).isEqualTo(1);
        // the losing racers rolled back their reservation instead of leaking stock
        assertThat(soldCount()).isEqualTo(1);
    }

    @Test
    void parallelMultiTicketOrdersStopAtCapacity() throws Exception {
        // each thread wants 2 of the 5 seats, so at most two orders can succeed
        List<String> outcomes = runInParallel(index -> {
            try {
                orderService.place(buyerId, "pair-" + index, new OrderCommand(eventId,
                        List.of(new OrderLine(freeTypeId, 2)), List.of("Guest A" + index, "Guest B" + index)));
                return "CONFIRMED";
            } catch (ApiException e) {
                return e.code();
            }
        });

        long confirmed = outcomes.stream().filter("CONFIRMED"::equals).count();
        assertThat(confirmed).isEqualTo(2);
        assertThat(soldCount()).isEqualTo(4);
        assertThat(ticketRepository.count()).isEqualTo(4);
    }
}
