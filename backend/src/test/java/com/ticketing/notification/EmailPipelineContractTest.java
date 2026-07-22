package com.ticketing.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.auth.PasswordResetService;
import com.ticketing.auth.RegistrationService;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.order.OrderCommand;
import com.ticketing.order.OrderLine;
import com.ticketing.order.OrderService;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every producer and the renderer meet only across the outbox as JSON. This drives each real
 * producer and renders the job it actually wrote, so a field renamed on one side is caught here.
 */
@Transactional
class EmailPipelineContractTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final String EVENT_TITLE = "Colombo Jazz Night";

    @Autowired
    RegistrationService registrationService;
    @Autowired
    PasswordResetService passwordResetService;
    @Autowired
    OrderService orderService;
    @Autowired
    EventService eventService;
    @Autowired
    ReminderEnqueuer reminderEnqueuer;
    @Autowired
    EmailContentFactory factory;
    @Autowired
    TicketTypeRepository ticketTypes;
    @Autowired
    UserRepository users;
    @Autowired
    OrganizerProfileRepository organizerProfiles;
    @Autowired
    OutboxJobRepository jobs;
    @Autowired
    JdbcTemplate jdbc;

    private UUID organizerUserId;
    private UUID organizerProfileId;
    private UUID buyerId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_jobs"); // isolate this run from any leftover jobs

        organizerUserId = user("Olivia Organizer");
        organizerProfileId = organizerProfiles.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();
        buyerId = user("Asha Perera");

        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerProfileId, new EventDraftCommand(
                CONCERTS, EVENT_TITLE, null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        ticketTypes.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS)));
        eventService.submitForReview(eventId, organizerProfileId, organizerUserId);
        eventService.approve(eventId, organizerUserId); // enqueues the decision email
        UUID typeId = ticketTypes.findByEventIdOrderByCreatedAtAsc(eventId).get(0).getId();
        orderService.place(buyerId, UUID.randomUUID().toString(), // enqueues the confirmation email
                new OrderCommand(eventId, List.of(new OrderLine(typeId, 1)), List.of("Asha Perera")));
    }

    private UUID user(String name) {
        return users.saveAndFlush(new User(UUID.randomUUID(),
                name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@example.com", "hash", name)).getId();
    }

    private String emailOf(UUID userId) {
        return users.findById(userId).orElseThrow().getEmail();
    }

    // renders the one job of the given kind that this run produced
    private EmailMessage renderEnqueued(String kind) {
        OutboxJob job = jobs.findAll().stream()
                .filter(j -> JobTypes.kindOf(j.getJobKey()).equals(kind))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected an enqueued " + kind + " job"));
        return factory.render(job.getJobKey(), job.getPayload());
    }

    @Test
    void registrationProducesAVerificationEmail() {
        registrationService.register("newcomer@example.com", "password123", "Newcomer");

        EmailMessage email = renderEnqueued(JobTypes.EMAIL_VERIFICATION);

        assertThat(email.to()).isEqualTo("newcomer@example.com");
        assertThat(email.subject()).contains("Verify");
        assertThat(email.body()).contains("/verify-email?token=");
    }

    @Test
    void aResetRequestProducesAPasswordResetEmail() {
        passwordResetService.requestReset(emailOf(buyerId));

        EmailMessage email = renderEnqueued(JobTypes.PASSWORD_RESET);

        assertThat(email.to()).isEqualTo(emailOf(buyerId));
        assertThat(email.subject()).contains("Reset");
        assertThat(email.body()).contains("/reset-password?token=");
    }

    @Test
    void placingAnOrderProducesAConfirmationToTheBuyer() {
        EmailMessage email = renderEnqueued(JobTypes.ORDER_CONFIRMATION);

        assertThat(email.to()).isEqualTo(emailOf(buyerId));
        assertThat(email.subject()).contains(EVENT_TITLE);
        assertThat(email.body()).contains("Asha Perera");
    }

    @Test
    void approvingAnEventProducesADecisionEmailToTheOrganizer() {
        EmailMessage email = renderEnqueued(JobTypes.EVENT_DECISION);

        assertThat(email.to()).isEqualTo(emailOf(organizerUserId));
        assertThat(email.subject()).contains("approved");
    }

    @Test
    void cancellingAnEventProducesACancellationToEachHolder() {
        eventService.cancelByOrganizer(eventId, organizerProfileId, organizerUserId);

        EmailMessage email = renderEnqueued(JobTypes.EVENT_CANCELLATION);

        assertThat(email.to()).isEqualTo(emailOf(buyerId));
        assertThat(email.subject()).contains("cancelled");
    }

    @Test
    void aReminderProducesAnEmailToTheHolder() {
        reminderEnqueuer.enqueueForEvent(eventId);

        EmailMessage email = renderEnqueued(JobTypes.REMINDER);

        assertThat(email.to()).isEqualTo(emailOf(buyerId));
        assertThat(email.subject()).contains(EVENT_TITLE).contains("coming up");
    }
}
