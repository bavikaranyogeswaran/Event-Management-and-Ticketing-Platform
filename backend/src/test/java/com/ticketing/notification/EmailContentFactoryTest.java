package com.ticketing.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class EmailContentFactoryTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    EmailContentFactory factory;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    EventService eventService;

    private UUID organizerUserId;
    private UUID organizerProfileId;
    private UUID buyerId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        User organizer = userRepository.saveAndFlush(new User(UUID.randomUUID(),
                "org." + UUID.randomUUID() + "@example.com", "hash", "Olivia Organizer"));
        organizerUserId = organizer.getId();
        organizerProfileId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();
        User buyer = userRepository.saveAndFlush(new User(UUID.randomUUID(),
                "buyer." + UUID.randomUUID() + "@example.com", "hash", "Asha Perera"));
        buyerId = buyer.getId();

        Instant now = Instant.now();
        eventId = eventService.createDraft(organizerProfileId, new EventDraftCommand(
                CONCERTS, "Colombo Jazz Night", null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    private String json(Object payload) {
        return objectMapper.writeValueAsString(payload);
    }

    private String buyerEmail() {
        return userRepository.findById(buyerId).orElseThrow().getEmail();
    }

    private String organizerEmail() {
        return userRepository.findById(organizerUserId).orElseThrow().getEmail();
    }

    @Test
    void verificationEmailCarriesTheLinkToTheRecipientInThePayload() {
        String payload = json(Map.of("to", "new@example.com", "displayName", "Newcomer",
                "link", "https://app/verify?token=RAW"));

        EmailMessage email = factory.render("EMAIL_VERIFICATION:" + UUID.randomUUID(), payload);

        assertThat(email.to()).isEqualTo("new@example.com");
        assertThat(email.subject()).contains("Verify");
        assertThat(email.body()).contains("Newcomer").contains("https://app/verify?token=RAW");
    }

    @Test
    void passwordResetEmailUsesTheLinkAndReassuresIfUnrequested() {
        String payload = json(Map.of("to", "back@example.com", "displayName", "Returner",
                "link", "https://app/reset?token=RAW"));

        EmailMessage email = factory.render("PASSWORD_RESET:" + UUID.randomUUID(), payload);

        assertThat(email.subject()).contains("Reset");
        assertThat(email.body()).contains("https://app/reset?token=RAW").contains("ignore");
    }

    @Test
    void orderConfirmationResolvesTheBuyerAndNamesTheEvent() {
        String payload = json(Map.of("orderId", UUID.randomUUID().toString(),
                "orderNumber", "ORD-2026-000042", "buyerId", buyerId.toString(),
                "eventId", eventId.toString(), "ticketCount", 2));

        EmailMessage email = factory.render("ORDER_CONFIRMATION:" + UUID.randomUUID(), payload);

        assertThat(email.to()).isEqualTo(buyerEmail());
        assertThat(email.subject()).isEqualTo("Your tickets for Colombo Jazz Night");
        assertThat(email.body()).contains("Asha Perera").contains("ORD-2026-000042").contains("2 ticket");
    }

    @Test
    void approvalGoesToTheOrganizerBehindTheProfile() {
        String payload = json(Map.of("eventId", eventId.toString(), "eventTitle", "Colombo Jazz Night",
                "organizerId", organizerProfileId.toString(), "decision", "APPROVED", "reason", ""));

        EmailMessage email = factory.render("EVENT_DECISION:" + eventId + ":" + UUID.randomUUID(), payload);

        // the payload carries the profile id; the email must reach the owning user
        assertThat(email.to()).isEqualTo(organizerEmail());
        assertThat(email.subject()).contains("approved");
        assertThat(email.body()).contains("Olivia Organizer").contains("published");
    }

    @Test
    void rejectionIncludesTheReason() {
        String payload = json(Map.of("eventId", eventId.toString(), "eventTitle", "Colombo Jazz Night",
                "organizerId", organizerProfileId.toString(), "decision", "REJECTED",
                "reason", "Venue details incomplete."));

        EmailMessage email = factory.render("EVENT_DECISION:" + eventId + ":" + UUID.randomUUID(), payload);

        assertThat(email.subject()).contains("needs changes");
        assertThat(email.body()).contains("Venue details incomplete.").contains("resubmit");
    }

    @Test
    void cancellationReachesTheTicketHolder() {
        String payload = json(Map.of("eventId", eventId.toString(), "eventTitle", "Colombo Jazz Night",
                "holderUserId", buyerId.toString()));

        EmailMessage email = factory.render("EVENT_CANCELLATION:" + eventId + ":" + buyerId, payload);

        assertThat(email.to()).isEqualTo(buyerEmail());
        assertThat(email.subject()).contains("cancelled");
        assertThat(email.body()).contains("Asha Perera").contains("no longer valid");
    }

    @Test
    void reminderReachesTheHolderAndNamesTheEvent() {
        String payload = json(Map.of("eventId", eventId.toString(), "holderUserId", buyerId.toString()));

        EmailMessage email = factory.render("REMINDER:" + eventId + ":" + buyerId, payload);

        assertThat(email.to()).isEqualTo(buyerEmail());
        assertThat(email.subject()).contains("Colombo Jazz Night").contains("coming up");
        assertThat(email.body()).contains("Asha Perera").contains("Colombo Jazz Night").contains("/tickets");
    }

    @Test
    void anUnknownJobKindFailsRatherThanSendingNothing() {
        assertThatThrownBy(() -> factory.render("SOMETHING_ELSE:1", "{}"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingRecipientFailsSoTheJobCanBeRetriedOrDeadLettered() {
        String payload = json(Map.of("orderId", UUID.randomUUID().toString(),
                "orderNumber", "ORD-2026-000099", "buyerId", UUID.randomUUID().toString(),
                "eventId", eventId.toString(), "ticketCount", 1));

        assertThatThrownBy(() -> factory.render("ORDER_CONFIRMATION:" + UUID.randomUUID(), payload))
                .isInstanceOf(IllegalStateException.class);
    }
}
