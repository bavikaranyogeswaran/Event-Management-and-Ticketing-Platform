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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.order.OrderCommand;
import com.ticketing.order.OrderLine;
import com.ticketing.order.OrderService;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(TestPaymentGatewayConfig.class)
class CheckoutApiTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE = "Checkout Gala";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    FakePaymentGateway gateway;
    @Autowired
    OrderService orderService;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID eventId;
    private UUID paidTypeId;
    private UUID freeTypeId;

    @BeforeEach
    void setUp() {
        gateway.reset();

        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, EVENT_TITLE, null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        paidTypeId = ticketType("VIP", new BigDecimal("1500.00"));
        freeTypeId = ticketType("Free", new BigDecimal("0.00"));

        eventService.submitForReview(eventId, profile.getId(), organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());
    }

    private UUID ticketType(String name, BigDecimal price) {
        Instant now = Instant.now();
        return ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, name, null, price,
                20, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();
    }

    private UUID createBuyer(String email, boolean verified) {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Buyer");
        user.addRole(Role.ATTENDEE);
        if (verified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        return userRepository.saveAndFlush(user).getId();
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private UUID placeOrder(UUID buyerId, String key, UUID typeId, int quantity) {
        return orderService.place(buyerId, key, new OrderCommand(eventId,
                List.of(new OrderLine(typeId, quantity)),
                quantity == 1 ? List.of("Asha") : List.of("Asha", "Nuwan"))).order().getId();
    }

    @Test
    void checkoutReturnsAPaymentPageForAHeldOrder() throws Exception {
        UUID buyerId = createBuyer("pay1@example.com", true);
        UUID orderId = placeOrder(buyerId, "co-1", paidTypeId, 2);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(login("pay1@example.com")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value(org.hamcrest.Matchers.startsWith("https://checkout.example/")));
    }

    @Test
    void theProviderIsAskedForExactlyWhatTheOrderSays() throws Exception {
        UUID buyerId = createBuyer("pay2@example.com", true);
        UUID orderId = placeOrder(buyerId, "co-2", paidTypeId, 2);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(login("pay2@example.com")).with(csrf()))
                .andExpect(status().isOk());

        CheckoutRequest sent = gateway.lastRequest();
        assertThat(sent.orderId()).isEqualTo(orderId);
        assertThat(sent.amountMinorUnits()).isEqualTo(300_000L); // 2 x 1500.00 LKR in cents
        assertThat(sent.currency()).isEqualTo("LKR");
        assertThat(sent.buyerEmail()).isEqualTo("pay2@example.com");
        assertThat(sent.description()).isEqualTo(EVENT_TITLE);
        assertThat(sent.successUrl()).contains(orderId.toString());
        assertThat(sent.cancelUrl()).contains(orderId.toString());
    }

    @Test
    void anAlreadyConfirmedOrderCannotBePaidFor() throws Exception {
        UUID buyerId = createBuyer("pay3@example.com", true);
        UUID freeOrderId = placeOrder(buyerId, "co-3", freeTypeId, 1);

        mockMvc.perform(post("/api/v1/orders/" + freeOrderId + "/checkout")
                        .cookie(login("pay3@example.com")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
    }

    @Test
    void aCancelledOrderCannotBePaidFor() throws Exception {
        UUID buyerId = createBuyer("pay4@example.com", true);
        UUID orderId = placeOrder(buyerId, "co-4", paidTypeId, 1);
        orderService.cancel(orderId, buyerId);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(login("pay4@example.com")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
    }

    @Test
    void anotherBuyerCannotStartCheckoutForSomeoneElsesOrder() throws Exception {
        UUID ownerId = createBuyer("payowner@example.com", true);
        UUID orderId = placeOrder(ownerId, "co-5", paidTypeId, 1);

        createBuyer("paystranger@example.com", true);
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(login("paystranger@example.com")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anUnverifiedBuyerCannotStartCheckout() throws Exception {
        UUID buyerId = createBuyer("payunverified@example.com", false);
        UUID orderId = placeOrder(buyerId, "co-6", paidTypeId, 1);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(login("payunverified@example.com")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void aProviderOutageIsReportedRatherThanFaked() throws Exception {
        UUID buyerId = createBuyer("payoutage@example.com", true);
        UUID orderId = placeOrder(buyerId, "co-7", paidTypeId, 1);
        gateway.goOffline(true);

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                        .cookie(login("payoutage@example.com")).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"));
    }

    @Test
    void checkoutCanBeRetriedWhileTheOrderStillHoldsItsSeats() throws Exception {
        UUID buyerId = createBuyer("payretry@example.com", true);
        UUID orderId = placeOrder(buyerId, "co-8", paidTypeId, 1);
        Cookie cookie = login("payretry@example.com");

        MvcResult first = mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                .cookie(cookie).with(csrf())).andExpect(status().isOk()).andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/orders/" + orderId + "/checkout")
                .cookie(cookie).with(csrf())).andExpect(status().isOk()).andReturn();

        // a buyer who closed the tab gets a fresh page rather than a dead one
        String firstUrl = JsonPath.read(first.getResponse().getContentAsString(), "$.checkoutUrl");
        String secondUrl = JsonPath.read(second.getResponse().getContentAsString(), "$.checkoutUrl");
        assertThat(firstUrl).isNotEqualTo(secondUrl);
        assertThat(gateway.capturedRequests()).hasSize(2);
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + UUID.randomUUID() + "/checkout").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
