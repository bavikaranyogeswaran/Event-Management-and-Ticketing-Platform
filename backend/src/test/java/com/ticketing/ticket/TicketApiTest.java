package com.ticketing.ticket;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class TicketApiTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mockMvc;
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
    private UUID freeTypeId;

    @BeforeEach
    void publishEvent() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, "Ticket Api Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        freeTypeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 4,
                now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();

        eventService.submitForReview(eventId, profile.getId(), organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());
    }

    private UUID createBuyer(String email) {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Buyer");
        user.addRole(Role.ATTENDEE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user).getId();
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private void placeOrder(UUID buyerId, String key, int quantity, List<String> attendees) {
        orderService.place(buyerId, key, new OrderCommand(eventId,
                List.of(new OrderLine(freeTypeId, quantity)), attendees));
    }

    @Test
    void ownerSeesTheirTicketsWithEventContext() throws Exception {
        UUID buyerId = createBuyer("tickets1@example.com");
        placeOrder(buyerId, "t-key-1", 2, List.of("Asha", "Nuwan"));
        Cookie cookie = login("tickets1@example.com");

        mockMvc.perform(get("/api/v1/users/me/tickets").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].publicCode").exists())
                .andExpect(jsonPath("$.items[0].eventTitle").exists())
                .andExpect(jsonPath("$.items[0].ticketTypeName").value("General"))
                .andExpect(jsonPath("$.items[0].status").value("VALID"))
                .andExpect(jsonPath("$.page.hasMore").value(false));
    }

    @Test
    void ticketListNeverExposesTheValidationToken() throws Exception {
        UUID buyerId = createBuyer("tickets2@example.com");
        placeOrder(buyerId, "t-key-2", 1, List.of("Asha"));
        Cookie cookie = login("tickets2@example.com");

        mockMvc.perform(get("/api/v1/users/me/tickets").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].validationTokenHash").doesNotExist())
                .andExpect(jsonPath("$.items[0].validationToken").doesNotExist());
    }

    @Test
    void listOnlyShowsTicketsOwnedByTheCaller() throws Exception {
        UUID mine = createBuyer("mine@example.com");
        UUID theirs = createBuyer("theirs@example.com");
        placeOrder(mine, "t-key-3", 1, List.of("Asha"));
        placeOrder(theirs, "t-key-4", 3, List.of("A", "B", "C"));

        mockMvc.perform(get("/api/v1/users/me/tickets").cookie(login("mine@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].attendeeName").value("Asha"));
    }

    @Test
    void listPaginatesAcrossPagesWithoutOverlap() throws Exception {
        UUID buyerId = createBuyer("pager@example.com");
        placeOrder(buyerId, "t-key-5", 3, List.of("A", "B", "C"));
        Cookie cookie = login("pager@example.com");

        MvcResult page1 = mockMvc.perform(get("/api/v1/users/me/tickets?limit=2").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andReturn();
        String body1 = page1.getResponse().getContentAsString();
        String cursor = JsonPath.read(body1, "$.page.nextCursor");
        List<String> firstIds = JsonPath.read(body1, "$.items[*].id");

        MvcResult page2 = mockMvc.perform(get("/api/v1/users/me/tickets?limit=2&cursor=" + cursor).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andReturn();
        List<String> secondIds = JsonPath.read(page2.getResponse().getContentAsString(), "$.items[*].id");

        Set<String> all = new HashSet<>(firstIds);
        all.addAll(secondIds);
        assertThat(all).hasSize(3);
    }

    @Test
    void ownerCanReadASingleTicket() throws Exception {
        UUID buyerId = createBuyer("single@example.com");
        placeOrder(buyerId, "t-key-6", 1, List.of("Asha"));
        Cookie cookie = login("single@example.com");

        MvcResult listed = mockMvc.perform(get("/api/v1/users/me/tickets").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        String ticketId = JsonPath.read(listed.getResponse().getContentAsString(), "$.items[0].id");

        mockMvc.perform(get("/api/v1/tickets/" + ticketId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.attendeeName").value("Asha"));
    }

    @Test
    void anotherUserCannotReadSomeoneElsesTicket() throws Exception {
        UUID owner = createBuyer("ticketowner@example.com");
        placeOrder(owner, "t-key-7", 1, List.of("Asha"));
        Cookie ownerCookie = login("ticketowner@example.com");
        MvcResult listed = mockMvc.perform(get("/api/v1/users/me/tickets").cookie(ownerCookie))
                .andExpect(status().isOk()).andReturn();
        String ticketId = JsonPath.read(listed.getResponse().getContentAsString(), "$.items[0].id");

        createBuyer("ticketstranger@example.com");
        mockMvc.perform(get("/api/v1/tickets/" + ticketId).cookie(login("ticketstranger@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanFetchTheTicketQrImage() throws Exception {
        UUID buyerId = createBuyer("qrowner@example.com");
        placeOrder(buyerId, "t-key-qr", 1, List.of("Asha"));
        Cookie cookie = login("qrowner@example.com");
        String ticketId = firstTicketId(cookie);

        mockMvc.perform(get("/api/v1/tickets/" + ticketId + "/qr").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                // the image is as good as the ticket, so caches must not keep a copy
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void anotherUserCannotFetchSomeoneElsesQr() throws Exception {
        UUID owner = createBuyer("qrreal@example.com");
        placeOrder(owner, "t-key-qr2", 1, List.of("Asha"));
        String ticketId = firstTicketId(login("qrreal@example.com"));

        createBuyer("qrthief@example.com");
        mockMvc.perform(get("/api/v1/tickets/" + ticketId + "/qr").cookie(login("qrthief@example.com")))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousCannotFetchAQr() throws Exception {
        mockMvc.perform(get("/api/v1/tickets/" + UUID.randomUUID() + "/qr"))
                .andExpect(status().isUnauthorized());
    }

    private String firstTicketId(Cookie cookie) throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/v1/users/me/tickets").cookie(cookie))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(listed.getResponse().getContentAsString(), "$.items[0].id");
    }
}
