package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class OrderIdempotencyTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    OrderIdempotency idempotency;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID buyerId;
    private UUID eventId;
    private UUID ticketTypeA;
    private UUID ticketTypeB;

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
                CONCERTS, "Idempotency Event", null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now, now.plus(29, ChronoUnit.DAYS), 500)).getId();

        ticketTypeA = UUID.fromString("a0000000-0000-4000-8000-000000000001");
        ticketTypeB = UUID.fromString("b0000000-0000-4000-8000-000000000002");
    }

    private OrderCommand command(List<OrderLine> items, List<String> attendees) {
        return new OrderCommand(eventId, items, attendees);
    }

    private Order savedOrder(String key, String fingerprint) {
        Order order = new Order(UUID.randomUUID(), "ORD-2026-000001", buyerId, eventId,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, key, fingerprint);
        return orderRepository.saveAndFlush(order);
    }

    @Test
    void sameRequestProducesSameFingerprint() {
        OrderCommand first = command(List.of(new OrderLine(ticketTypeA, 2)), List.of("Asha", "Nuwan"));
        OrderCommand second = command(List.of(new OrderLine(ticketTypeA, 2)), List.of("Asha", "Nuwan"));
        assertThat(idempotency.fingerprint(first)).isEqualTo(idempotency.fingerprint(second));
    }

    @Test
    void differentQuantityProducesDifferentFingerprint() {
        String one = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 2)), List.of("Asha", "Nuwan")));
        String two = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 3)), List.of("Asha", "Nuwan")));
        assertThat(one).isNotEqualTo(two);
    }

    @Test
    void reorderingItemsKeepsTheSameFingerprint() {
        OrderCommand ab = command(List.of(new OrderLine(ticketTypeA, 1), new OrderLine(ticketTypeB, 2)), List.of("Asha"));
        OrderCommand ba = command(List.of(new OrderLine(ticketTypeB, 2), new OrderLine(ticketTypeA, 1)), List.of("Asha"));
        assertThat(idempotency.fingerprint(ab)).isEqualTo(idempotency.fingerprint(ba));
    }

    @Test
    void swappingAttendeeNamesIsADifferentRequest() {
        String forward = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 2)), List.of("Asha", "Nuwan")));
        String reversed = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 2)), List.of("Nuwan", "Asha")));
        assertThat(forward).isNotEqualTo(reversed);
    }

    @Test
    void aNameContainingASeparatorCannotImitateTwoNames() {
        String joined = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 1)), List.of("Asha|5:Nuwan")));
        String split = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 2)), List.of("Asha", "Nuwan")));
        assertThat(joined).isNotEqualTo(split);
    }

    @Test
    void unusedKeyReturnsEmpty() {
        assertThat(idempotency.findReplay(buyerId, "unused-key", "any-hash")).isEmpty();
    }

    @Test
    void sameKeyAndSameRequestReturnsTheOriginalOrder() {
        String fingerprint = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 1)), List.of("Asha")));
        Order original = savedOrder("key-1", fingerprint);

        assertThat(idempotency.findReplay(buyerId, "key-1", fingerprint))
                .get()
                .extracting(Order::getId)
                .isEqualTo(original.getId());
    }

    @Test
    void sameKeyWithADifferentRequestIsAConflict() {
        savedOrder("key-2", idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 1)), List.of("Asha"))));
        String otherFingerprint = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 5)), List.of("Asha")));

        assertThatThrownBy(() -> idempotency.findReplay(buyerId, "key-2", otherFingerprint))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void keysAreScopedToTheUser() {
        String fingerprint = idempotency.fingerprint(command(List.of(new OrderLine(ticketTypeA, 1)), List.of("Asha")));
        savedOrder("shared-key", fingerprint);

        assertThat(idempotency.findReplay(UUID.randomUUID(), "shared-key", fingerprint)).isEmpty();
    }
}
