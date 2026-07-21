package com.ticketing.checkin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventStaffAssignment;
import com.ticketing.event.EventStaffAssignmentRepository;
import com.ticketing.event.EventType;
import com.ticketing.order.OrderCommand;
import com.ticketing.order.OrderLine;
import com.ticketing.order.OrderService;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.Role;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketTokenFactory;
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
class CheckInApiTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    TicketTokenFactory tokenFactory;
    @Autowired
    OrderService orderService;
    @Autowired
    EventService eventService;
    @Autowired
    EventStaffAssignmentRepository assignments;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    jakarta.persistence.EntityManager entityManager;
    @Autowired
    PasswordEncoder passwordEncoder;

    private UUID organizerUserId;
    private UUID eventId;
    private Cookie staff;
    private Ticket ticket;

    @BeforeEach
    void setUp() throws Exception {
        User organizerUser = createUser("org", Role.ORGANIZER);
        organizerUserId = organizerUser.getId();
        UUID organizerId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();

        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "CheckIn Api Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        UUID typeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();
        eventService.submitForReview(eventId, organizerId, organizerUserId);
        eventService.approve(eventId, organizerUserId);

        UUID buyerId = createUser("buyer", Role.ATTENDEE).getId();
        ticket = orderService.place(buyerId, "ci-api-key",
                new OrderCommand(eventId, List.of(new OrderLine(typeId, 1)), List.of("Asha"))).tickets().get(0);

        User staffUser = createUser("staff", Role.STAFF);
        assignments.saveAndFlush(new EventStaffAssignment(UUID.randomUUID(), eventId, staffUser.getId(), organizerUserId));
        staff = login(staffUser.getEmail());
    }

    private User createUser(String label, Role role) {
        User user = new User(UUID.randomUUID(), label + "." + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("password123"), label);
        user.addRole(role);
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.saveAndFlush(user);
    }

    private Cookie login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private String tokenBody() {
        return "{\"eventId\":\"" + eventId + "\",\"token\":\"" + tokenFactory.rawToken(ticket.getId()) + "\"}";
    }

    private String codeBody() {
        return "{\"eventId\":\"" + eventId + "\",\"publicCode\":\"" + ticket.getPublicCode() + "\"}";
    }

    private MvcResult checkIn(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/check-ins").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    // flush (not clear) so the check-in's pending ticket update reaches the shared connection
    private String ticketStatus() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.getId());
    }

    @Test
    void aValidTicketIsAdmittedAndMarkedUsed() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.attendeeName").value("Asha"))
                .andExpect(jsonPath("$.checkedInAt").exists())
                .andExpect(jsonPath("$.method").value("QR"));

        assertThat(ticketStatus()).isEqualTo("USED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM check_ins WHERE ticket_id = ?",
                Integer.class, ticket.getId())).isEqualTo(1);
    }

    @Test
    void aManualCodeEntryIsRecordedAsManual() throws Exception {
        checkIn(codeBody());

        assertThat(jdbc.queryForObject("SELECT method FROM check_ins WHERE ticket_id = ?",
                String.class, ticket.getId())).isEqualTo("MANUAL");
    }

    @Test
    void asecondCheckInReportsTheOriginalTime() throws Exception {
        MvcResult first = checkIn(tokenBody());
        String firstTime = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.checkedInAt");

        mockMvc.perform(post("/api/v1/check-ins").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_CHECKED_IN"))
                .andExpect(jsonPath("$.details.checkedInAt").value(firstTime));

        // the duplicate attempt left exactly one check-in
        assertThat(jdbc.queryForObject("SELECT count(*) FROM check_ins WHERE ticket_id = ?",
                Integer.class, ticket.getId())).isEqualTo(1);
    }

    @Test
    void aCancelledTicketIsRejected() throws Exception {
        jdbc.update("UPDATE tickets SET status = 'CANCELLED' WHERE id = ?", ticket.getId());
        entityManager.clear();

        mockMvc.perform(post("/api/v1/check-ins").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TICKET_CANCELLED"));
    }

    @Test
    void anUnknownTokenIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\",\"token\":\"nope\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    void aTicketForAnotherEventIsRejectedDistinctly() throws Exception {
        UUID otherEventId = publishAnotherEvent();

        mockMvc.perform(post("/api/v1/check-ins").cookie(adminCookie()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + otherEventId + "\",\"token\":\""
                                + tokenFactory.rawToken(ticket.getId()) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WRONG_EVENT"));
    }

    @Test
    void unassignedStaffCannotCheckIn() throws Exception {
        User stranger = createUser("stranger", Role.STAFF);

        mockMvc.perform(post("/api/v1/check-ins").cookie(login(stranger.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_TO_EVENT"));

        assertThat(ticketStatus()).isEqualTo("VALID"); // the refused attempt admitted nothing
    }

    @Test
    void anonymousCannotCheckIn() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(tokenBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aScanWithoutATokenOrCodeIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void anOrdinaryRejectionCarriesTheStandardEnvelopeWithoutDetails() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\",\"token\":\"nope\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.requestId").exists())
                // the details map is omitted unless an error actually carries some
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    private UUID publishAnotherEvent() {
        Instant now = Instant.now();
        UUID organizerId = organizerProfileRepository.findByUserId(organizerUserId).orElseThrow().getId();
        UUID other = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "Other CheckIn Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), other, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS)));
        eventService.submitForReview(other, organizerId, organizerUserId);
        eventService.approve(other, organizerUserId);
        return other;
    }

    private Cookie adminCookie() throws Exception {
        User admin = createUser("admin", Role.ADMIN);
        return login(admin.getEmail());
    }
}
