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

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.tickettype.TicketTypeStatus;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every rejection path checked through the HTTP error envelope, not just the service. */
@AutoConfigureMockMvc
@Transactional
class OrderErrorApiTest extends AbstractIntegrationTest {

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
    PasswordEncoder passwordEncoder;

    private UUID organizerUserId;
    private UUID organizerProfileId;
    private Cookie buyer;

    private record Fixture(UUID eventId, UUID ticketTypeId) {
    }

    @BeforeEach
    void setUp() throws Exception {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        organizerUserId = organizerUser.getId();
        organizerProfileId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Test Org", null, null)).getId();

        User user = new User(UUID.randomUUID(), "errbuyer@example.com",
                passwordEncoder.encode("password123"), "Buyer");
        user.addRole(Role.ATTENDEE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.saveAndFlush(user);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"errbuyer@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        buyer = result.getResponse().getCookie("SESSION");
    }

    private Fixture fixture(boolean publish, BigDecimal price, int quantityTotal, int maxPerOrder) {
        Instant now = Instant.now();
        UUID eventId = eventService.createDraft(organizerProfileId, new EventDraftCommand(
                CONCERTS, "Err Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        UUID typeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                price, quantityTotal, maxPerOrder,
                now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();

        if (publish) {
            eventService.submitForReview(eventId, organizerProfileId, organizerUserId);
            eventService.approve(eventId, organizerUserId);
        }
        return new Fixture(eventId, typeId);
    }

    private Fixture freeFixture() {
        return fixture(true, new BigDecimal("0.00"), 10, 4);
    }

    private String body(Fixture fixture, int quantity, int attendeeCount) {
        StringBuilder attendees = new StringBuilder();
        for (int i = 0; i < attendeeCount; i++) {
            attendees.append(i == 0 ? "" : ",").append("{\"name\":\"Guest ").append(i).append("\"}");
        }
        return "{\"eventId\":\"" + fixture.eventId() + "\","
                + "\"items\":[{\"ticketTypeId\":\"" + fixture.ticketTypeId() + "\",\"quantity\":" + quantity + "}],"
                + "\"attendees\":[" + attendees + "]}";
    }

    private org.springframework.test.web.servlet.ResultActions order(String key, String body) throws Exception {
        var request = post("/api/v1/orders").cookie(buyer).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    @Test
    void blankIdempotencyKeyIsRejected() throws Exception {
        order(" ", body(freeFixture(), 1, 1))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void overLongIdempotencyKeyIsRejected() throws Exception {
        order("k".repeat(81), body(freeFixture(), 1, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unpublishedEventIsNotOnSale() throws Exception {
        Fixture draft = fixture(false, new BigDecimal("0.00"), 10, 4);
        order("err-1", body(draft, 1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_ON_SALE"));
    }

    @Test
    void aPaidOrderIsHeldAwaitingPaymentWithNoTicketsYet() throws Exception {
        Fixture paid = fixture(true, new BigDecimal("1500.00"), 10, 4);
        order("err-2", body(paid, 2, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.grandTotal").value(3000.00))
                .andExpect(jsonPath("$.tickets.length()").value(0));
    }

    @Test
    void inactiveTicketTypeIsNotAvailable() throws Exception {
        Fixture fixture = freeFixture();
        TicketType type = ticketTypeRepository.findById(fixture.ticketTypeId()).orElseThrow();
        type.setStatus(TicketTypeStatus.INACTIVE);
        ticketTypeRepository.saveAndFlush(type);

        order("err-3", body(fixture, 1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_TYPE_NOT_AVAILABLE"));
    }

    @Test
    void exceedingMaxPerOrderIsRejected() throws Exception {
        order("err-4", body(freeFixture(), 5, 5))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_LIMIT_EXCEEDED"));
    }

    @Test
    void orderingMoreThanRemainingStockIsRejected() throws Exception {
        Fixture small = fixture(true, new BigDecimal("0.00"), 3, 4);
        order("err-5a", body(small, 3, 3)).andExpect(status().isCreated());

        order("err-5b", body(small, 1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_INVENTORY_EXHAUSTED"));
    }

    @Test
    void sameKeyWithADifferentBasketIsAConflict() throws Exception {
        Fixture fixture = freeFixture();
        order("err-6", body(fixture, 1, 1)).andExpect(status().isCreated());

        order("err-6", body(fixture, 2, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void attendeeCountMustMatchTicketCount() throws Exception {
        order("err-7", body(freeFixture(), 2, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void emptyBasketFailsBeanValidationWithFieldErrors() throws Exception {
        Fixture fixture = freeFixture();
        String body = "{\"eventId\":\"" + fixture.eventId() + "\",\"items\":[],"
                + "\"attendees\":[{\"name\":\"Asha\"}]}";

        order("err-8", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void blankAttendeeNameFailsBeanValidation() throws Exception {
        Fixture fixture = freeFixture();
        String body = "{\"eventId\":\"" + fixture.eventId() + "\","
                + "\"items\":[{\"ticketTypeId\":\"" + fixture.ticketTypeId() + "\",\"quantity\":1}],"
                + "\"attendees\":[{\"name\":\"  \"}]}";

        order("err-9", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\","
                + "\"items\":[{\"ticketTypeId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}],"
                + "\"attendees\":[{\"name\":\"Asha\"}]}";

        order("err-10", body)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void everyErrorCarriesTheStandardEnvelope() throws Exception {
        order("err-11", body(fixture(false, new BigDecimal("0.00"), 10, 4), 1, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }
}
