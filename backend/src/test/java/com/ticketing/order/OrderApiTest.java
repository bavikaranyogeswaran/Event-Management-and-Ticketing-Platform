package com.ticketing.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class OrderApiTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderItemRepository orderItemRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID eventId;
    private UUID freeTypeId;

    @BeforeEach
    void publishEventWithFreeTickets() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, "Api Order Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        freeTypeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 10, 4,
                now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();

        eventService.submitForReview(eventId, profile.getId(), organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());
    }

    private Cookie login(String email, boolean emailVerified) throws Exception {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Buyer");
        user.addRole(Role.ATTENDEE);
        if (emailVerified) {
            user.setEmailVerifiedAt(Instant.now());
        }
        userRepository.saveAndFlush(user);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private String orderBody(int quantity, String... attendeeNames) {
        StringBuilder attendees = new StringBuilder();
        for (int i = 0; i < attendeeNames.length; i++) {
            attendees.append(i == 0 ? "" : ",").append("{\"name\":\"").append(attendeeNames[i]).append("\"}");
        }
        return "{\"eventId\":\"" + eventId + "\","
                + "\"items\":[{\"ticketTypeId\":\"" + freeTypeId + "\",\"quantity\":" + quantity + "}],"
                + "\"attendees\":[" + attendees + "]}";
    }

    @Test
    void freeOrderIsCreatedWithATicketPerAttendee() throws Exception {
        Cookie cookie = login("buyer1@example.com", true);

        mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .header("Idempotency-Key", "api-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(2, "Asha", "Nuwan")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.currency").value("LKR"))
                .andExpect(jsonPath("$.grandTotal").value(0))
                .andExpect(jsonPath("$.orderNumber").exists())
                .andExpect(jsonPath("$.tickets.length()").value(2))
                .andExpect(jsonPath("$.tickets[0].attendeeName").value("Asha"))
                .andExpect(jsonPath("$.tickets[0].status").value("VALID"))
                .andExpect(jsonPath("$.tickets[0].publicCode").exists());
    }

    @Test
    void ticketsNeverExposeTheValidationToken() throws Exception {
        Cookie cookie = login("buyer2@example.com", true);

        mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .header("Idempotency-Key", "api-key-2")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(1, "Asha")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tickets[0].validationTokenHash").doesNotExist())
                .andExpect(jsonPath("$.tickets[0].validationToken").doesNotExist());
    }

    @Test
    void repeatingTheSameKeyReturnsTheOriginalOrder() throws Exception {
        Cookie cookie = login("buyer3@example.com", true);
        String body = orderBody(2, "Asha", "Nuwan");

        MvcResult first = mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .header("Idempotency-Key", "repeat-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String orderId = JsonPath.read(first.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .header("Idempotency-Key", "repeat-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.tickets.length()").value(2));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        Cookie cookie = login("buyer4@example.com", true);

        mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(1, "Asha")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void unverifiedEmailCannotOrder() throws Exception {
        Cookie cookie = login("buyer5@example.com", false);

        mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .header("Idempotency-Key", "api-key-5")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(1, "Asha")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/orders").with(csrf())
                        .header("Idempotency-Key", "api-key-6")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(1, "Asha")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCanReadTheirOwnOrder() throws Exception {
        Cookie cookie = login("buyer7@example.com", true);

        MvcResult placed = mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .header("Idempotency-Key", "api-key-7")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(1, "Asha")))
                .andExpect(status().isCreated()).andReturn();
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.tickets.length()").value(1));
    }

    // paid orders cannot be placed through the API yet, so a held order is written directly
    private UUID heldOrderFor(UUID ownerId) {
        Order order = new Order(UUID.randomUUID(), "ORD-2026-000777", ownerId, eventId,
                new BigDecimal("1500.00"), BigDecimal.ZERO, new BigDecimal("1500.00"), "held-key", "hash");
        order.holdUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        orderRepository.saveAndFlush(order);
        orderItemRepository.saveAndFlush(new OrderItem(UUID.randomUUID(), order.getId(), freeTypeId, "General",
                new BigDecimal("0.00"), 1, new BigDecimal("0.00")));
        return order.getId();
    }

    @Test
    void buyerCanCancelTheirHeldOrder() throws Exception {
        Cookie cookie = login("canceller@example.com", true);
        UUID orderId = heldOrderFor(currentUserId("canceller@example.com"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookie).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingAnAlreadyConfirmedOrderIsRejected() throws Exception {
        Cookie cookie = login("cancelconfirmed@example.com", true);

        MvcResult placed = mockMvc.perform(post("/api/v1/orders").cookie(cookie).with(csrf())
                        .header("Idempotency-Key", "cancel-key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(1, "Asha")))
                .andExpect(status().isCreated()).andReturn();
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(cookie).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    }

    @Test
    void anotherUserCannotCancelSomeoneElsesOrder() throws Exception {
        login("cancelowner@example.com", true);
        UUID orderId = heldOrderFor(currentUserId("cancelowner@example.com"));

        Cookie stranger = login("cancelstranger@example.com", true);
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/cancel").cookie(stranger).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private UUID currentUserId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void anotherUserCannotReadSomeoneElsesOrder() throws Exception {
        Cookie owner = login("owner@example.com", true);
        MvcResult placed = mockMvc.perform(post("/api/v1/orders").cookie(owner).with(csrf())
                        .header("Idempotency-Key", "api-key-8")
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(1, "Asha")))
                .andExpect(status().isCreated()).andReturn();
        String orderId = JsonPath.read(placed.getResponse().getContentAsString(), "$.id");

        Cookie stranger = login("stranger@example.com", true);
        mockMvc.perform(get("/api/v1/orders/" + orderId).cookie(stranger))
                .andExpect(status().isNotFound());
    }
}
