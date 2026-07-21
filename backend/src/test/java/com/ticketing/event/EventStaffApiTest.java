package com.ticketing.event;

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
class EventStaffApiTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EventStaffAssignmentRepository assignments;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie organizer;
    private UUID organizerProfileId;
    private UUID eventId;
    private UUID otherEventId;
    private UUID doorStaffId;

    @BeforeEach
    void setUp() throws Exception {
        organizer = loginAsOrganizer("staffowner@example.com");
        eventId = newEvent("Door Event");
        otherEventId = newEvent("Second Event");
        doorStaffId = createUser("door@example.com").getId();
    }

    private User createUser(String email) {
        User user = new User(UUID.randomUUID(), email, passwordEncoder.encode("password123"), "Door Person");
        user.addRole(Role.ATTENDEE);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    /** Remembers this organizer's own profile; other tests leave profiles behind in the database. */
    private Cookie loginAsOrganizer(String email) throws Exception {
        User user = createUser(email);
        user.addRole(Role.ORGANIZER);
        userRepository.saveAndFlush(user);
        organizerProfileId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), user.getId(), "Org", null, null)).getId();
        return login(email);
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private UUID newEvent(String title) {
        Instant now = Instant.now();
        return eventService.createDraft(organizerProfileId, new EventDraftCommand(
                CONCERTS, title + " " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    private MvcResult assign(UUID event, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/organizer/events/" + event + "/staff")
                        .cookie(organizer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andReturn();
    }

    private boolean hasStaffRole(UUID userId) {
        return userRepository.findById(userId).orElseThrow().hasRole(Role.STAFF);
    }

    @Test
    void assigningStaffAddsThemToTheEventAndGrantsTheRole() throws Exception {
        MvcResult result = assign(eventId, "door@example.com");

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getContentAsString()).contains("door@example.com");
        assertThat(assignments.existsByEventIdAndUserId(eventId, doorStaffId)).isTrue();
        assertThat(hasStaffRole(doorStaffId)).isTrue();
    }

    @Test
    void theRoleAloneDoesNotReachAnotherEvent() throws Exception {
        assign(eventId, "door@example.com");

        // holding STAFF is not the same as being on this door
        assertThat(hasStaffRole(doorStaffId)).isTrue();
        assertThat(assignments.existsByEventIdAndUserId(otherEventId, doorStaffId)).isFalse();
    }

    @Test
    void anUnknownEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/staff")
                        .cookie(organizer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STAFF_USER_NOT_FOUND"));
    }

    @Test
    void assigningTheSamePersonTwiceIsRejected() throws Exception {
        assign(eventId, "door@example.com");

        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/staff")
                        .cookie(organizer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"door@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAFF_ALREADY_ASSIGNED"));
    }

    @Test
    void staffAreListedForTheirEvent() throws Exception {
        assign(eventId, "door@example.com");

        mockMvc.perform(get("/api/v1/organizer/events/" + eventId + "/staff").cookie(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("door@example.com"))
                .andExpect(jsonPath("$[0].displayName").value("Door Person"))
                .andExpect(jsonPath("$[0].assignedAt").exists());
    }

    @Test
    void removingTheLastAssignmentTakesTheRoleAway() throws Exception {
        assign(eventId, "door@example.com");
        assertThat(hasStaffRole(doorStaffId)).isTrue();

        mockMvc.perform(delete("/api/v1/organizer/events/" + eventId + "/staff/" + doorStaffId)
                        .cookie(organizer).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(assignments.existsByEventIdAndUserId(eventId, doorStaffId)).isFalse();
        // nothing left to authorise, so the role goes too
        assertThat(hasStaffRole(doorStaffId)).isFalse();
    }

    @Test
    void removingOneOfTwoAssignmentsKeepsTheRole() throws Exception {
        assign(eventId, "door@example.com");
        assign(otherEventId, "door@example.com");

        mockMvc.perform(delete("/api/v1/organizer/events/" + eventId + "/staff/" + doorStaffId)
                        .cookie(organizer).with(csrf()))
                .andExpect(status().isNoContent());

        // still working the other door
        assertThat(hasStaffRole(doorStaffId)).isTrue();
        assertThat(assignments.existsByEventIdAndUserId(otherEventId, doorStaffId)).isTrue();
    }

    @Test
    void removingSomeoneWhoWasNeverAssignedIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/organizer/events/" + eventId + "/staff/" + doorStaffId)
                        .cookie(organizer).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherOrganizerCannotStaffThisEvent() throws Exception {
        Cookie intruder = loginAsOrganizer("otherorg@example.com");

        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/staff")
                        .cookie(intruder).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"door@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMalformedEmailIsRejectedBeforeAnythingIsLookedUp() throws Exception {
        mockMvc.perform(post("/api/v1/organizer/events/" + eventId + "/staff")
                        .cookie(organizer).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
