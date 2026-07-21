package com.ticketing.checkin;

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
import com.ticketing.event.EventStaffAssignment;
import com.ticketing.event.EventStaffAssignmentRepository;
import com.ticketing.event.EventType;
import com.ticketing.order.OrderCommand;
import com.ticketing.order.OrderLine;
import com.ticketing.order.OrderService;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.security.CurrentUser;
import com.ticketing.shared.security.Role;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketTokenFactory;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the last hard invariant under real parallelism: however many devices scan a ticket at
 * once, it is admitted exactly one time (R-06). The unique ticket_id is what decides the winner.
 */
class CheckInConcurrencyTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Scan Race Event";
    private static final int THREADS = 16;

    @Autowired
    CheckInService checkInService;
    @Autowired
    TicketTokenFactory tokenFactory;
    @Autowired
    OrderService orderService;
    @Autowired
    EventService eventService;
    @Autowired
    EventStaffAssignmentRepository assignments;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;
    @Autowired
    JdbcTemplate jdbc;

    private UUID staffUserId;
    private CurrentUser staff;
    private UUID eventId;
    private UUID typeId;

    @BeforeEach
    void setUp() {
        clearTickets();

        User organizerUser = user("org");
        UUID organizerId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Org", null, null)).getId();

        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, EVENT_TITLE_PREFIX + " " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        typeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();
        eventService.submitForReview(eventId, organizerId, organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());

        User staffUser = user("staff");
        staffUserId = staffUser.getId();
        assignments.saveAndFlush(new EventStaffAssignment(UUID.randomUUID(), eventId, staffUserId, organizerUser.getId()));
        staff = new CurrentUser(staffUserId, staffUser.getEmail(), Set.of(Role.STAFF), true);
    }

    @AfterEach
    void tearDown() {
        clearTickets();
        jdbc.update("DELETE FROM event_staff_assignments WHERE event_id IN "
                + "(SELECT id FROM events WHERE title LIKE ?)", EVENT_TITLE_PREFIX + "%");
        jdbc.update("DELETE FROM ticket_types WHERE event_id IN (SELECT id FROM events WHERE title LIKE ?)",
                EVENT_TITLE_PREFIX + "%");
        jdbc.update("DELETE FROM events WHERE title LIKE ?", EVENT_TITLE_PREFIX + "%");
    }

    private void clearTickets() {
        jdbc.update("DELETE FROM check_ins");
        jdbc.update("DELETE FROM tickets");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM outbox_jobs");
    }

    private User user(String label) {
        return userRepository.saveAndFlush(
                new User(UUID.randomUUID(), label + "." + UUID.randomUUID() + "@example.com", "hash", label));
    }

    private Ticket issueTicket() {
        UUID buyerId = user("buyer").getId();
        return orderService.place(buyerId, "scan-" + UUID.randomUUID(),
                new OrderCommand(eventId, List.of(new OrderLine(typeId, 1)), List.of("Asha"))).tickets().get(0);
    }

    private CheckInCommand scan(Ticket ticket) {
        return new CheckInCommand(eventId, tokenFactory.rawToken(ticket.getId()), null);
    }

    private int checkInCount(UUID ticketId) {
        return jdbc.queryForObject("SELECT count(*) FROM check_ins WHERE ticket_id = ?", Integer.class, ticketId);
    }

    private List<String> runInParallel(java.util.function.Supplier<String> attempt) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    startGun.await();
                    return attempt.get();
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
    void oneTicketScannedByManyDevicesIsAdmittedExactlyOnce() throws Exception {
        Ticket ticket = issueTicket();

        List<String> outcomes = runInParallel(() -> {
            try {
                checkInService.checkIn(scan(ticket), staff);
                return "ADMITTED";
            } catch (ApiException e) {
                return e.code();
            }
        });

        long admitted = outcomes.stream().filter("ADMITTED"::equals).count();
        assertThat(admitted).isEqualTo(1);
        // everyone else was told it was already used, never some other failure
        assertThat(outcomes).filteredOn(outcome -> !"ADMITTED".equals(outcome))
                .containsOnly("ALREADY_CHECKED_IN");

        assertThat(checkInCount(ticket.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.getId()))
                .isEqualTo("USED");
    }

    @Test
    void everyLosingScanReportsTheSameOriginalTime() throws Exception {
        Ticket ticket = issueTicket();

        List<String> times = runInParallel(() -> {
            try {
                checkInService.checkIn(scan(ticket), staff);
                return "winner";
            } catch (ApiException e) {
                Object when = e.details().get("checkedInAt");
                return when == null ? "no-time" : when.toString();
            }
        });

        // one winner, and every loser saw the identical admission time
        assertThat(times).filteredOn("winner"::equals).hasSize(1);
        assertThat(Set.copyOf(times.stream().filter(t -> !"winner".equals(t)).toList())).hasSize(1);
    }

    @Test
    void manyDistinctTicketsScannedAtOnceAllGetIn() throws Exception {
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tickets.add(issueTicket());
        }

        // a busy door: different tickets scanned simultaneously must not interfere
        List<String> outcomes = runInParallelIndexed(index -> {
            try {
                checkInService.checkIn(scan(tickets.get(index)), staff);
                return "ADMITTED";
            } catch (ApiException e) {
                return e.code();
            }
        });

        assertThat(outcomes).containsOnly("ADMITTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM check_ins WHERE event_id = ?",
                Integer.class, eventId)).isEqualTo(THREADS);
    }

    private List<String> runInParallelIndexed(java.util.function.IntFunction<String> attempt) throws Exception {
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
}
