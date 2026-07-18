package com.ticketing.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class OrganizerEventApiTest extends AbstractIntegrationTest {

    private static final String CONCERTS = "c0000000-0000-4000-8000-000000000001";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie loginAsOrganizer(String email) throws Exception {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Organizer");
        user.addRole(Role.ATTENDEE);
        user.addRole(Role.ORGANIZER);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.saveAndFlush(user);
        organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), user.getId(), "Org", null, null));
        return login(email);
    }

    private Cookie loginAsAttendee(String email) throws Exception {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Attendee");
        user.addRole(Role.ATTENDEE);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.saveAndFlush(user);
        return login(email);
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private String eventBody(String title) {
        Instant now = Instant.now();
        return "{"
                + "\"categoryId\":\"" + CONCERTS + "\","
                + "\"title\":\"" + title + "\","
                + "\"eventType\":\"PHYSICAL\","
                + "\"venueName\":\"Trace Expert City\",\"city\":\"Colombo\","
                + "\"timezone\":\"Asia/Colombo\","
                + "\"startsAt\":\"" + now.plus(30, ChronoUnit.DAYS) + "\","
                + "\"endsAt\":\"" + now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS) + "\","
                + "\"registrationOpensAt\":\"" + now + "\","
                + "\"registrationClosesAt\":\"" + now.plus(29, ChronoUnit.DAYS) + "\","
                + "\"capacity\":150}";
    }

    private String ticketBody() {
        Instant now = Instant.now();
        return "{\"name\":\"General\",\"price\":1500.00,\"quantityTotal\":100,\"maxPerOrder\":4,"
                + "\"salesStartAt\":\"" + now + "\",\"salesEndAt\":\"" + now.plus(28, ChronoUnit.DAYS) + "\"}";
    }

    private String createEvent(Cookie cookie, String title) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/organizer/events").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody(title)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createAddTicketAndSeeItInDetail() throws Exception {
        Cookie cookie = loginAsOrganizer("org1@example.com");
        String eventId = createEvent(cookie, "Colombo Jazz Night");

        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/ticket-types").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("General"));

        mockMvc.perform(get("/api/v1/organizer/events/" + eventId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.ticketTypes.length()").value(1))
                .andExpect(jsonPath("$.ticketTypes[0].name").value("General"));
    }

    @Test
    void submitMovesEventToPendingReview() throws Exception {
        Cookie cookie = loginAsOrganizer("org2@example.com");
        String eventId = createEvent(cookie, "Submittable");
        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/ticket-types").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/submit").cookie(cookie).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void listReturnsOrganizersEventsPaginated() throws Exception {
        Cookie cookie = loginAsOrganizer("org3@example.com");
        createEvent(cookie, "Event One");
        createEvent(cookie, "Event Two");

        mockMvc.perform(get("/api/v1/organizer/events").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page.hasMore").value(false));
    }

    @Test
    void attendeeWithoutOrganizerRoleIsForbidden() throws Exception {
        Cookie cookie = loginAsAttendee("plain@example.com");
        mockMvc.perform(post("/api/v1/organizer/events").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody("Nope")))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCannotSeeAnothersEvent() throws Exception {
        Cookie owner = loginAsOrganizer("owner@example.com");
        String eventId = createEvent(owner, "Private Event");

        Cookie other = loginAsOrganizer("other@example.com");
        mockMvc.perform(get("/api/v1/organizer/events/" + eventId).cookie(other))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelTransitionsEventToCancelled() throws Exception {
        Cookie cookie = loginAsOrganizer("canceller@example.com");
        String eventId = createEvent(cookie, "Cancellable");
        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/ticket-types").cookie(cookie).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(ticketBody()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/submit").cookie(cookie).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/cancel").cookie(cookie).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/organizer/events/" + eventId).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void deleteRemovesDraftFromView() throws Exception {
        Cookie cookie = loginAsOrganizer("deleter@example.com");
        String eventId = createEvent(cookie, "Disposable Draft");

        mockMvc.perform(delete("/api/v1/organizer/events/" + eventId).cookie(cookie).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/organizer/events/" + eventId).cookie(cookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void listPaginatesAcrossPagesWithoutOverlap() throws Exception {
        Cookie cookie = loginAsOrganizer("pager@example.com");
        String id1 = createEvent(cookie, "Event A");
        String id2 = createEvent(cookie, "Event B");
        String id3 = createEvent(cookie, "Event C");

        var page1 = mockMvc.perform(get("/api/v1/organizer/events?limit=2").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page.hasMore").value(true))
                .andReturn();
        String body1 = page1.getResponse().getContentAsString();
        String cursor = JsonPath.read(body1, "$.page.nextCursor");
        List<String> firstIds = JsonPath.read(body1, "$.items[*].id");

        var page2 = mockMvc.perform(get("/api/v1/organizer/events?limit=2&cursor=" + cursor).cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page.hasMore").value(false))
                .andReturn();
        List<String> secondIds = JsonPath.read(page2.getResponse().getContentAsString(), "$.items[*].id");

        Set<String> all = new HashSet<>(firstIds);
        all.addAll(secondIds);
        assertThat(all).containsExactlyInAnyOrder(id1, id2, id3);
    }
}
