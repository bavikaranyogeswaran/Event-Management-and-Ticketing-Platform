package com.ticketing.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole chain in one go: signed HTTP delivery through to a confirmed order with tickets. */
@AutoConfigureMockMvc
@Transactional
@Import(TestPaymentGatewayConfig.class)
class PaymentWebhookFlowTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String URL = "/api/v1/webhooks/payments/stripe";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    FakePaymentGateway gateway;
    @Autowired
    OrderService orderService;
    @Autowired
    OrderRepository orders;
    @Autowired
    TicketRepository tickets;
    @Autowired
    PaymentRepository payments;
    @Autowired
    TicketTypeRepository ticketTypes;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID buyerId;
    private UUID eventId;
    private UUID paidTypeId;

    @BeforeEach
    void setUp() {
        gateway.reset();

        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        buyerId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "buyer." + UUID.randomUUID() + "@example.com", "hash", "Buyer")).getId();
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, "Webhook Flow Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        paidTypeId = ticketTypes.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "VIP", null,
                new BigDecimal("1500.00"), 10, 4,
                now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();

        eventService.submitForReview(eventId, profile.getId(), organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());
    }

    private UUID placePaidOrder(String key) {
        return orderService.place(buyerId, key, new OrderCommand(eventId,
                List.of(new OrderLine(paidTypeId, 2)), List.of("Asha", "Nuwan"))).order().getId();
    }

    private void deliver(String signature) throws Exception {
        mockMvc.perform(post(URL)
                        .header("Stripe-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_flow\",\"type\":\"checkout.session.completed\"}"))
                .andExpect(signature.equals(FakePaymentGateway.VALID_SIGNATURE)
                        ? status().isOk()
                        : status().isBadRequest());
    }

    @Test
    void aSignedDeliveryTurnsAHeldOrderIntoTickets() throws Exception {
        UUID orderId = placePaidOrder("flow-1");
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        gateway.willReturn(new PaymentEvent("evt_flow", PaymentEventType.PAYMENT_SUCCEEDED,
                orderId, "pi_flow_1", 300_000L, "LKR", null));
        deliver(FakePaymentGateway.VALID_SIGNATURE);

        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(2);
        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_flow_1")).isPresent();
    }

    @Test
    void anUnsignedDeliveryChangesNothingAtAll() throws Exception {
        UUID orderId = placePaidOrder("flow-2");

        gateway.willReturn(new PaymentEvent("evt_flow", PaymentEventType.PAYMENT_SUCCEEDED,
                orderId, "pi_flow_2", 300_000L, "LKR", null));
        deliver("t=1,v1=forged");

        // a forged request must not reach the order at all
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).isEmpty();
        assertThat(payments.findByProviderAndProviderPaymentId(PaymentProvider.STRIPE, "pi_flow_2")).isEmpty();
    }

    @Test
    void repeatDeliveriesOverHttpStillProduceOneSetOfTickets() throws Exception {
        UUID orderId = placePaidOrder("flow-3");
        gateway.willReturn(new PaymentEvent("evt_flow", PaymentEventType.PAYMENT_SUCCEEDED,
                orderId, "pi_flow_3", 300_000L, "LKR", null));

        deliver(FakePaymentGateway.VALID_SIGNATURE);
        deliver(FakePaymentGateway.VALID_SIGNATURE);
        deliver(FakePaymentGateway.VALID_SIGNATURE);

        assertThat(tickets.findByOrderIdOrderByIssuedAtAsc(orderId)).hasSize(2);
        assertThat(payments.findByOrderIdOrderByCreatedAtAsc(orderId)).hasSize(1);
    }
}
