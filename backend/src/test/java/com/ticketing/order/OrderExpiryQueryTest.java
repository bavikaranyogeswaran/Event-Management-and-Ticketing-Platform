package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OrderExpiryQueryTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    @Autowired
    OrderRepository orders;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID buyerId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        buyerId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "buyer." + UUID.randomUUID() + "@example.com", "hash", "Buyer")).getId();
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, "Expiry Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    private Order held(String key, Instant expiresAt) {
        Order order = new Order(UUID.randomUUID(), "ORD-2026-" + key, buyerId, eventId,
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("1500.00"), key, "hash");
        order.holdUntil(expiresAt);
        return orders.saveAndFlush(order);
    }

    @Test
    void onlyOrdersPastTheirDeadlineAreReturned() {
        held("000001", NOW.minus(5, ChronoUnit.MINUTES));
        held("000002", NOW.plus(5, ChronoUnit.MINUTES));

        assertThat(orders.findDueForExpiry(NOW, Limit.of(10)))
                .extracting(Order::getIdempotencyKey)
                .containsExactly("000001");
    }

    @Test
    void anOrderExactlyAtItsDeadlineIsDue() {
        held("000003", NOW);
        assertThat(orders.findDueForExpiry(NOW, Limit.of(10))).hasSize(1);
    }

    @Test
    void confirmedOrdersAreNeverSweptEvenWhenOverdue() {
        Order paid = held("000004", NOW.minus(1, ChronoUnit.HOURS));
        paid.confirm(NOW);
        orders.saveAndFlush(paid);

        assertThat(orders.findDueForExpiry(NOW, Limit.of(10))).isEmpty();
    }

    @Test
    void alreadyExpiredOrdersAreNotPickedUpAgain() {
        Order gone = held("000005", NOW.minus(1, ChronoUnit.HOURS));
        gone.expire(NOW);
        orders.saveAndFlush(gone);

        assertThat(orders.findDueForExpiry(NOW, Limit.of(10))).isEmpty();
    }

    @Test
    void freeOrdersWithoutADeadlineAreIgnored() {
        // a confirmed free order never carries expires_at, so it must not surface here
        Order free = new Order(UUID.randomUUID(), "ORD-2026-000006", buyerId, eventId,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "000006", "hash");
        free.confirm(NOW);
        orders.saveAndFlush(free);

        assertThat(orders.findDueForExpiry(NOW, Limit.of(10))).isEmpty();
    }

    @Test
    void theOldestHoldsComeFirstAndTheBatchIsCapped() {
        held("000007", NOW.minus(1, ChronoUnit.MINUTES));
        held("000008", NOW.minus(30, ChronoUnit.MINUTES));
        held("000009", NOW.minus(10, ChronoUnit.MINUTES));

        assertThat(orders.findDueForExpiry(NOW, Limit.of(2)))
                .extracting(Order::getIdempotencyKey)
                .containsExactly("000008", "000009");
    }
}
