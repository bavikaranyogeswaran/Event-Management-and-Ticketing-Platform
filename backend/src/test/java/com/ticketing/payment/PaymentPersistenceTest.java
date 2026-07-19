package com.ticketing.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.order.Order;
import com.ticketing.order.OrderRepository;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class PaymentPersistenceTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    PaymentRepository payments;
    @Autowired
    OrderRepository orders;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID orderId;

    @BeforeEach
    void setUp() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        UUID buyerId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "buyer." + UUID.randomUUID() + "@example.com", "hash", "Buyer")).getId();
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        UUID eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, "Payment Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        Order order = new Order(UUID.randomUUID(), "ORD-2026-000900", buyerId, eventId,
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("1500.00"), "pay-key", "hash");
        orderId = orders.saveAndFlush(order).getId();
    }

    private Payment payment(String providerPaymentId) {
        Payment payment = new Payment(UUID.randomUUID(), orderId, PaymentProvider.STRIPE,
                new BigDecimal("1500.00"), "LKR");
        if (providerPaymentId != null) {
            payment.markSucceeded(providerPaymentId, "evt_1", Instant.now());
        }
        return payment;
    }

    @Test
    void aNewPaymentStartsAsCreated() {
        Payment saved = payments.saveAndFlush(payment(null));

        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(saved.getProvider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(saved.getCurrency()).isEqualTo("LKR");
        assertThat(saved.getAmount()).isEqualByComparingTo("1500.00");
        assertThat(saved.getPaidAt()).isNull();
    }

    @Test
    void succeedingRecordsTheProviderReferences() {
        Payment saved = payments.saveAndFlush(payment("pi_success_1"));

        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(saved.getProviderPaymentId()).isEqualTo("pi_success_1");
        assertThat(saved.getProviderEventId()).isEqualTo("evt_1");
        assertThat(saved.getPaidAt()).isNotNull();
    }

    @Test
    void failingRecordsTheReasonWithoutAPaidTimestamp() {
        Payment failed = new Payment(UUID.randomUUID(), orderId, PaymentProvider.STRIPE,
                new BigDecimal("1500.00"), "LKR");
        failed.markFailed("pi_failed_1", "evt_2", "card_declined");
        Payment saved = payments.saveAndFlush(failed);

        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo("card_declined");
        assertThat(saved.getPaidAt()).isNull();
    }

    @Test
    void lookupByProviderPaymentIdFindsTheRecordedPayment() {
        payments.saveAndFlush(payment("pi_lookup_1"));

        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_lookup_1")).isPresent();
        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_absent")).isEmpty();
    }

    @Test
    void theSameProviderPaymentCannotBeRecordedTwice() {
        payments.saveAndFlush(payment("pi_duplicate"));

        // this is the defence a replayed webhook runs into
        assertThatThrownBy(() -> payments.saveAndFlush(payment("pi_duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void severalCheckoutAttemptsCanSitWithoutAProviderPaymentId() {
        // checkout records an attempt before any provider payment exists, so nulls must not collide
        payments.saveAndFlush(payment(null));
        payments.saveAndFlush(payment(null));

        assertThat(payments.findByOrderIdOrderByCreatedAtAsc(orderId)).hasSize(2);
    }
}
