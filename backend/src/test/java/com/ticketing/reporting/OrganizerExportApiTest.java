package com.ticketing.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
import com.ticketing.file.TestObjectStorageConfig;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.servlet.http.Cookie;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
@Import(TestObjectStorageConfig.class)
class OrganizerExportApiTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired OrganizerProfileRepository organizerProfileRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EventService eventService;

    /** Creates an organizer with a profile and a draft event. Returns the event id. */
    private SetupResult createOrganizerWithEvent(String emailPrefix) throws Exception {
        String email = emailPrefix + "." + UUID.randomUUID() + "@example.com";
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Organizer");
        user.addRole(Role.ATTENDEE);
        user.addRole(Role.ORGANIZER);
        user.setEmailVerifiedAt(Instant.now());
        userRepository.saveAndFlush(user);
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), user.getId(), "Org", null, null));

        Instant now = Instant.now();
        var event = eventService.createDraft(profile.getId(), new EventDraftCommand(CONCERTS, "Export Test", "desc",
                EventType.PHYSICAL, "Venue", "Addr", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                now, now.plus(29, ChronoUnit.DAYS), 100));
        eventService.addTicketType(event.getId(), profile.getId(), new TicketTypeCommand("GA", null,
                new BigDecimal("500.00"), 50, 4, now, now.plus(28, ChronoUnit.DAYS)));

        Cookie session = login(email);
        return new SetupResult(event.getId(), session);
    }

    private Cookie login(String email) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return r.getResponse().getCookie("SESSION");
    }

    record SetupResult(UUID eventId, Cookie session) {}

    @Test
    void triggerExportReturns202WithFileId() throws Exception {
        SetupResult setup = createOrganizerWithEvent("exp1");

        mockMvc.perform(post("/api/v1/organizer/events/" + setup.eventId() + "/attendees/export")
                        .cookie(setup.session()).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.fileId").exists());
    }

    @Test
    void exportForAnotherOrganizersEventReturnsNotFound() throws Exception {
        SetupResult owner = createOrganizerWithEvent("exp2owner");
        SetupResult other = createOrganizerWithEvent("exp2other");

        mockMvc.perform(post("/api/v1/organizer/events/" + owner.eventId() + "/attendees/export")
                        .cookie(other.session()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        SetupResult setup = createOrganizerWithEvent("exp3");

        mockMvc.perform(post("/api/v1/organizer/events/" + setup.eventId() + "/attendees/export")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
