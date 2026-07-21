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
class CheckInValidateApiTest extends AbstractIntegrationTest {

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
    private UUID organizerId;
    private UUID eventId;
    private UUID typeId;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        User organizerUser = createUser("org", Role.ORGANIZER);
        organizerUserId = organizerUser.getId();
        organizerId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();

        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "Validate Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        typeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();
        eventService.submitForReview(eventId, organizerId, organizerUserId);
        eventService.approve(eventId, organizerUserId);

        UUID buyerId = createUser("buyer", Role.ATTENDEE).getId();
        ticket = orderService.place(buyerId, "val-key",
                new OrderCommand(eventId, List.of(new OrderLine(typeId, 1)), List.of("Asha"))).tickets().get(0);
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

    private Cookie staffAssignedToEvent() throws Exception {
        User staff = createUser("staff", Role.STAFF);
        assignments.saveAndFlush(new EventStaffAssignment(UUID.randomUUID(), eventId, staff.getId(), organizerUserId));
        return login(staff.getEmail());
    }

    private String body(String tokenOrNull, String codeOrNull) {
        String token = tokenOrNull == null ? "" : ",\"token\":\"" + tokenOrNull + "\"";
        String code = codeOrNull == null ? "" : ",\"publicCode\":\"" + codeOrNull + "\"";
        return "{\"eventId\":\"" + eventId + "\"" + token + code + "}";
    }

    private String scannedToken() {
        return tokenFactory.rawToken(ticket.getId());
    }

    @Test
    void aValidTicketReportsThatItWouldBeAdmitted() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(staffAssignedToEvent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketId").value(ticket.getId().toString()))
                .andExpect(jsonPath("$.attendeeName").value("Asha"))
                .andExpect(jsonPath("$.ticketTypeName").value("General"))
                .andExpect(jsonPath("$.ticketStatus").value("VALID"))
                .andExpect(jsonPath("$.checkInAllowed").value(true))
                .andExpect(jsonPath("$.checkedInAt").doesNotExist());
    }

    @Test
    void thePublicCodePathWorksToo() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(staffAssignedToEvent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(null, ticket.getPublicCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInAllowed").value(true));
    }

    @Test
    void validatingChangesNothing() throws Exception {
        Cookie staff = staffAssignedToEvent();
        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(staff).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isOk());

        // still valid, still unadmitted after a dry run
        assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.getId()))
                .isEqualTo("VALID");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM check_ins WHERE ticket_id = ?",
                Integer.class, ticket.getId())).isZero();
    }

    @Test
    void anAlreadyUsedTicketIsDescribedWithItsOriginalTime() throws Exception {
        UUID checkInId = UUID.randomUUID();
        jdbc.update("INSERT INTO check_ins (id, ticket_id, event_id, staff_user_id, checked_in_at, method) "
                + "VALUES (?, ?, ?, ?, now() - interval '10 minutes', 'QR')",
                checkInId, ticket.getId(), eventId, organizerUserId);
        jdbc.update("UPDATE tickets SET status = 'USED' WHERE id = ?", ticket.getId());
        entityManager.clear(); // the raw update bypassed the session; re-read from the database

        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(staffAssignedToEvent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketStatus").value("USED"))
                .andExpect(jsonPath("$.checkInAllowed").value(false))
                .andExpect(jsonPath("$.checkedInAt").exists());
    }

    @Test
    void aCancelledTicketIsDescribedButNotAdmissible() throws Exception {
        jdbc.update("UPDATE tickets SET status = 'CANCELLED' WHERE id = ?", ticket.getId());
        entityManager.clear();

        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(staffAssignedToEvent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.checkInAllowed").value(false));
    }

    @Test
    void anUnknownTokenIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(staffAssignedToEvent()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("no-such-token", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    void unassignedStaffAreRefused() throws Exception {
        User stranger = createUser("stranger", Role.STAFF);

        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(login(stranger.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_TO_EVENT"));
    }

    @Test
    void aPlainAttendeeCannotValidate() throws Exception {
        User attendee = createUser("nosy", Role.ATTENDEE);

        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(login(attendee.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_TO_EVENT"));
    }

    @Test
    void theOwningOrganizerCanValidate() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/validate").cookie(login(organizerEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInAllowed").value(true));
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/check-ins/validate").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(scannedToken(), null)))
                .andExpect(status().isUnauthorized());
    }

    private String organizerEmail() {
        return userRepository.findById(organizerUserId).orElseThrow().getEmail();
    }
}
