package com.ticketing.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class EventServiceTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    EventService eventService;
    @Autowired
    EventRepository eventRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID organizerId;
    private UUID organizerUserId;
    private UUID adminUserId;

    @BeforeEach
    void setUpOrganizer() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        organizerUserId = organizerUser.getId();
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Test Org", null, null));
        organizerId = profile.getId();

        User adminUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "admin." + UUID.randomUUID() + "@example.com", "hash", "Admin"));
        adminUserId = adminUser.getId();
    }

    private EventDraftCommand validDraft(String title) {
        Instant now = Instant.now();
        return new EventDraftCommand(CONCERTS, title, "A great show", EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now, now.plus(29, ChronoUnit.DAYS), 150);
    }

    private TicketTypeCommand validTicket() {
        Instant now = Instant.now();
        return new TicketTypeCommand("General", null, new BigDecimal("1500.00"), 100, 4,
                now, now.plus(28, ChronoUnit.DAYS));
    }

    private Event draftWithTicket(String title) {
        Event event = eventService.createDraft(organizerId, validDraft(title));
        eventService.addTicketType(event.getId(), organizerId, validTicket());
        return event;
    }

    private Event reload(Event event) {
        return eventRepository.findById(event.getId()).orElseThrow();
    }

    @Test
    void createGeneratesSlugAndStartsAsDraft() {
        Event event = eventService.createDraft(organizerId, validDraft("Colombo Jazz Night"));
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getSlug()).isEqualTo("colombo-jazz-night");
    }

    @Test
    void duplicateTitlesGetDistinctSlugs() {
        Event a = eventService.createDraft(organizerId, validDraft("Same Title"));
        Event b = eventService.createDraft(organizerId, validDraft("Same Title"));
        assertThat(a.getSlug()).isNotEqualTo(b.getSlug());
    }

    @Test
    void rejectsPhysicalEventWithoutVenue() {
        EventDraftCommand noVenue = new EventDraftCommand(CONCERTS, "No Venue", null, EventType.PHYSICAL,
                null, null, null, null, "Asia/Colombo",
                Instant.now().plus(10, ChronoUnit.DAYS), Instant.now().plus(11, ChronoUnit.DAYS),
                Instant.now(), Instant.now().plus(9, ChronoUnit.DAYS), 50);
        assertThatThrownBy(() -> eventService.createDraft(organizerId, noVenue))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("VENUE_REQUIRED"));
    }

    @Test
    void cannotSubmitWithoutTicketType() {
        Event event = eventService.createDraft(organizerId, validDraft("No Tickets"));
        assertThatThrownBy(() -> eventService.submitForReview(event.getId(), organizerId, organizerUserId))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("PUBLICATION_RULES_FAILED"));
    }

    @Test
    void fullApprovalFlowPublishesEvent() {
        Event event = draftWithTicket("Approvable Event");
        eventService.submitForReview(event.getId(), organizerId, organizerUserId);
        assertThat(reload(event).getStatus()).isEqualTo(EventStatus.PENDING_REVIEW);

        eventService.approve(event.getId(), adminUserId);
        assertThat(reload(event).getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(reload(event).getPublishedAt()).isNotNull();
    }

    @Test
    void rejectRequiresReasonAndReturnsToRejected() {
        Event event = draftWithTicket("Rejectable Event");
        eventService.submitForReview(event.getId(), organizerId, organizerUserId);

        assertThatThrownBy(() -> eventService.reject(event.getId(), adminUserId, "  "))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("REASON_REQUIRED"));

        eventService.reject(event.getId(), adminUserId, "Incomplete details");
        assertThat(reload(event).getStatus()).isEqualTo(EventStatus.REJECTED);
        assertThat(reload(event).getRejectionReason()).isEqualTo("Incomplete details");
    }

    @Test
    void cannotEditWhilePendingReview() {
        Event event = draftWithTicket("Locked Event");
        eventService.submitForReview(event.getId(), organizerId, organizerUserId);
        assertThatThrownBy(() -> eventService.updateEvent(event.getId(), organizerId, validDraft("New Title")))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("EVENT_NOT_EDITABLE"));
    }

    @Test
    void withdrawReturnsPendingEventToDraft() {
        Event event = draftWithTicket("Withdrawable");
        eventService.submitForReview(event.getId(), organizerId, organizerUserId);
        eventService.withdraw(event.getId(), organizerId);
        assertThat(reload(event).getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void ownershipIsEnforced() {
        Event event = draftWithTicket("Someone Elses");
        UUID otherOrganizer = UUID.randomUUID();
        assertThatThrownBy(() -> eventService.submitForReview(event.getId(), otherOrganizer, organizerUserId))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("RESOURCE_NOT_FOUND"));
    }

    @Test
    void ticketPriceLocksAfterSale() {
        Event event = draftWithTicket("Selling Event");
        UUID ticketTypeId = ticketTypeRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).get(0).getId();

        // simulate a sale the way the order flow will (SQL counter bump), then read fresh
        jdbc.update("UPDATE ticket_types SET quantity_sold = 10 WHERE id = ?", ticketTypeId);
        entityManager.clear();

        TicketTypeCommand priceChange = new TicketTypeCommand("General", null, new BigDecimal("2000.00"),
                100, 4, Instant.now(), Instant.now().plus(20, ChronoUnit.DAYS));
        assertThatThrownBy(() -> eventService.updateTicketType(event.getId(), ticketTypeId, organizerId, priceChange))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo("TICKET_TYPE_PRICE_LOCKED"));
    }

    @Test
    void ticketQuantityCanIncreaseAfterSale() {
        Event event = draftWithTicket("Topup Event");
        TicketType original = ticketTypeRepository.findByEventIdOrderByCreatedAtAsc(event.getId()).get(0);
        UUID ticketTypeId = original.getId();

        jdbc.update("UPDATE ticket_types SET quantity_sold = 10 WHERE id = ?", ticketTypeId);
        entityManager.clear();

        TicketTypeCommand moreInventory = new TicketTypeCommand("General", null, original.getPrice(),
                200, 4, Instant.now(), Instant.now().plus(20, ChronoUnit.DAYS));
        TicketType updated = eventService.updateTicketType(event.getId(), ticketTypeId, organizerId, moreInventory);
        assertThat(updated.getQuantityTotal()).isEqualTo(200);
    }
}
