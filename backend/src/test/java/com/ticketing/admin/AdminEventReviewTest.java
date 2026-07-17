package com.ticketing.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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
import com.ticketing.event.TicketTypeCommand;
import com.ticketing.notification.OutboxJobRepository;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class AdminEventReviewTest extends AbstractIntegrationTest {

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
    OutboxJobRepository outboxJobRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    /** Creates an event submitted for review, returns its id. */
    private UUID pendingEvent() {
        User organizer = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Org"));
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizer.getId(), "Org", null, null));
        UUID orgId = profile.getId();

        Instant now = Instant.now();
        var event = eventService.createDraft(orgId, new EventDraftCommand(CONCERTS, "Reviewable", "desc",
                EventType.PHYSICAL, "Venue", "Addr", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                now, now.plus(29, ChronoUnit.DAYS), 100));
        eventService.addTicketType(event.getId(), orgId, new TicketTypeCommand("GA", null,
                new BigDecimal("1000.00"), 50, 4, now, now.plus(28, ChronoUnit.DAYS)));
        eventService.submitForReview(event.getId(), orgId, organizer.getId());
        return event.getId();
    }

    private Cookie loginAsAdmin(String email) throws Exception {
        User admin = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Admin");
        admin.addRole(Role.ADMIN);
        userRepository.saveAndFlush(admin);
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("SESSION");
    }

    @Test
    void queueListsPendingEventsAndApprovalPublishes() throws Exception {
        UUID eventId = pendingEvent();
        Cookie admin = loginAsAdmin("admin1@example.com");

        mockMvc.perform(get("/api/v1/admin/events?status=PENDING_REVIEW").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + eventId + "')]").exists());

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/review").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(outboxJobRepository.count()).isPositive(); // decision email queued
    }

    @Test
    void rejectRequiresReason() throws Exception {
        UUID eventId = pendingEvent();
        Cookie admin = loginAsAdmin("admin2@example.com");

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/review").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        mockMvc.perform(post("/api/v1/admin/events/" + eventId + "/review").cookie(admin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"Needs more detail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void nonAdminCannotReachTheQueue() throws Exception {
        User organizer = new User(UUID.randomUUID(), "notadmin@example.com",
                passwordEncoder.encode("password123"), "Org");
        organizer.addRole(Role.ORGANIZER);
        userRepository.saveAndFlush(organizer);
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"notadmin@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie cookie = r.getResponse().getCookie("SESSION");

        mockMvc.perform(get("/api/v1/admin/events").cookie(cookie))
                .andExpect(status().isForbidden());
    }
}
