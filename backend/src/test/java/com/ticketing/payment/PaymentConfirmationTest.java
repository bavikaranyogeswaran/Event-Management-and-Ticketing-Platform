package com.ticketing.payment;

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

/** Runs without a wrapping transaction so each delivery commits the way a real one would. */
class PaymentConfirmationTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE_PREFIX = "Confirm Event";

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
                new BigDecimal("1500.00"), 10, 4,
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

    private UUID placePaidOrder(String key, int quantity) {
        List<String> attendees = quantity == 1 ? List.of("Asha") : List.of("Asha", "Nuwan");
        return orderService.place(buyerId, key, new OrderCommand(eventId,
                List.of(new OrderLine(paidTypeId, quantity)), attendees)).order().getId();
    }

    private PaymentEvent paidEvent(UUID orderId, String paymentId, long amountMinor, String currency) {
        return new PaymentEvent("evt_" + paymentId, PaymentEventType.PAYMENT_SUCCEEDED,
                orderId, paymentId, amountMinor, currency, null);
    }

    private OrderStatus statusOf(UUID orderId) {
        return orders.findById(orderId).orElseThrow().getStatus();
    }

    private int soldCount() {
        return jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?", Integer.class, paidTypeId);
    }

    @Test
    void aSuccessfulPaymentConfirmsTheOrderAndIssuesTickets() {
        UUID orderId = placePaidOrder("pc-1", 2);

        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_1", 300_000L, "LKR"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(2);
        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_1"))
                .get().satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
                    assertThat(payment.getPaidAt()).isNotNull();
                });
    }

    @Test
    void ticketsCarryTheNamesGivenWhenTheOrderWasPlaced() {
        UUID orderId = placePaidOrder("pc-2", 2);

        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_2", 300_000L, "LKR"));

        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId))
                .extracting(t -> t.getAttendeeName())
                .containsExactlyInAnyOrder("Asha", "Nuwan");
    }

    @Test
    void theConfirmationEmailIsQueuedOnlyOncePaid() {
        UUID orderId = placePaidOrder("pc-3", 1);
        assertThat(queuedConfirmations(orderId)).isZero();

        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_3", 150_000L, "LKR"));

        assertThat(queuedConfirmations(orderId)).isEqualTo(1);
    }

    private int queuedConfirmations(UUID orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_jobs WHERE job_key = ?",
                Integer.class, "ORDER_CONFIRMATION:" + orderId);
    }

    @Test
    void redeliveringTheSamePaymentChangesNothing() {
        UUID orderId = placePaidOrder("pc-4", 2);
        PaymentEvent event = paidEvent(orderId, "pi_4", 300_000L, "LKR");

        webhook.handle(PaymentProvider.STRIPE, event);
        webhook.handle(PaymentProvider.STRIPE, event);
        webhook.handle(PaymentProvider.STRIPE, event);

        assertThat(payments.findByOrderIdOrderByCreatedAtAsc(orderId)).hasSize(1);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(2);
        assertThat(queuedConfirmations(orderId)).isEqualTo(1);
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    void aPaymentForTheWrongAmountIsRecordedButNotHonoured() {
        UUID orderId = placePaidOrder("pc-5", 2);

        // order costs 3000.00; the provider reports a hundredth of that
        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_5", 3_000L, "LKR"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).isEmpty();
        // the money is still on file rather than silently dropped
        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_5")).isPresent();
    }

    @Test
    void aPaymentInTheWrongCurrencyIsNotHonoured() {
        UUID orderId = placePaidOrder("pc-6", 1);

        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_6", 150_000L, "USD"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).isEmpty();
    }

    @Test
    void aFailedPaymentLeavesTheOrderPayable() {
        UUID orderId = placePaidOrder("pc-7", 1);

        webhook.handle(PaymentProvider.STRIPE, new PaymentEvent("evt_f", PaymentEventType.PAYMENT_FAILED,
                orderId, "pi_7", 150_000L, "LKR", "card_declined"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(soldCount()).isEqualTo(1); // the seats are still held for a retry
        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_7"))
                .get().satisfies(payment -> {
                    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
                    assertThat(payment.getFailureCode()).isEqualTo("card_declined");
                    assertThat(payment.getPaidAt()).isNull();
                });
    }

    @Test
    void aFailedPaymentCanStillBeFollowedByASuccessfulOne() {
        UUID orderId = placePaidOrder("pc-8", 1);

        webhook.handle(PaymentProvider.STRIPE, new PaymentEvent("evt_f2", PaymentEventType.PAYMENT_FAILED,
                orderId, "pi_8a", 150_000L, "LKR", "card_declined"));
        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_8b", 150_000L, "LKR"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(1);
    }

    @Test
    void aPaymentLandingAfterExpiryTakesTheSeatsBackWhenTheyAreFree() {
        UUID orderId = placePaidOrder("pc-9", 2);
        expire(orderId);
        assertThat(soldCount()).isZero();

        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_9", 300_000L, "LKR"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(2);
        assertThat(soldCount()).isEqualTo(2);
    }

    @Test
    void aPaymentLandingAfterTheSeatsSoldOutIsRecordedForRefund() {
        UUID orderId = placePaidOrder("pc-10", 2);
        expire(orderId);
        // someone else takes every remaining seat before the payment arrives
        placePaidOrder("pc-10-other", 2);
        jdbc.update("UPDATE ticket_types SET quantity_sold = quantity_total WHERE id = ?", paidTypeId);

        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_10", 300_000L, "LKR"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).isEmpty();
        // the payment stays on record so the money can be traced and refunded by hand
        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_10"))
                .get().extracting(Payment::getStatus).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(soldCount()).isEqualTo(10); // nothing oversold to honour it
    }

    @Test
    void aPartlyReclaimableOrderGivesBackWhateverItTook() {
        // two lines: the first can be reclaimed after expiry, the second cannot
        UUID secondTypeId = ticketTypes.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "Balcony", null,
                new BigDecimal("1500.00"), 10, 4,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(28, ChronoUnit.DAYS))).getId();

        UUID orderId = orderService.place(buyerId, "pc-12", new OrderCommand(eventId,
                List.of(new OrderLine(paidTypeId, 1), new OrderLine(secondTypeId, 1)),
                List.of("Asha", "Nuwan"))).order().getId();

        jdbc.update("UPDATE ticket_types SET quantity_sold = 0 WHERE id IN (?, ?)", paidTypeId, secondTypeId);
        jdbc.update("UPDATE orders SET status = 'EXPIRED', cancelled_at = now() WHERE id = ?", orderId);
        // only the second line's seats are gone
        jdbc.update("UPDATE ticket_types SET quantity_sold = quantity_total WHERE id = ?", secondTypeId);

        webhook.handle(PaymentProvider.STRIPE, paidEvent(orderId, "pi_12", 300_000L, "LKR"));

        assertThat(statusOf(orderId)).isEqualTo(OrderStatus.EXPIRED);
        // the first line must not be left holding seats for an order that was never confirmed
        assertThat(soldCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT quantity_sold FROM ticket_types WHERE id = ?",
                Integer.class, secondTypeId)).isEqualTo(10);
    }

    @Test
    void anEventForAnUnknownOrderIsAcknowledgedWithoutEffect() {
        webhook.handle(PaymentProvider.STRIPE, paidEvent(UUID.randomUUID(), "pi_11", 150_000L, "LKR"));

        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_11")).isEmpty();
    }

    @Test
    void anIgnoredEventTypeDoesNothing() {
        webhook.handle(PaymentProvider.STRIPE, PaymentEvent.ignored("evt_ignored"));

        assertThat(payments.count()).isZero();
    }

    private void expire(UUID orderId) {
        jdbc.update("UPDATE orders SET expires_at = now() - interval '1 minute' WHERE id = ?", orderId);
        orders.findById(orderId).orElseThrow();
        jdbc.update("UPDATE ticket_types SET quantity_sold = quantity_sold - "
                + "(SELECT sum(quantity) FROM order_items WHERE order_id = ?) WHERE id = ?", orderId, paidTypeId);
        jdbc.update("UPDATE orders SET status = 'EXPIRED', cancelled_at = now() WHERE id = ?", orderId);
    }
}
