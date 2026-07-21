package com.ticketing.checkin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.order.OrderCommand;
import com.ticketing.order.OrderLine;
import com.ticketing.order.OrderService;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.api.ApiException;
import com.ticketing.ticket.Ticket;
import com.ticketing.ticket.TicketTokenFactory;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class TicketResolverTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    TicketResolver resolver;
    @Autowired
    TicketTokenFactory tokenFactory;
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

    private UUID organizerId;
    private UUID organizerUserId;
    private UUID buyerId;
    private UUID eventId;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        organizerUserId = organizerUser.getId();
        buyerId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "buyer." + UUID.randomUUID() + "@example.com", "hash", "Buyer")).getId();
        organizerId = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();

        eventId = publishedEventWithFreeTicket();
        ticket = orderService.place(buyerId, "resolve-key",
                new OrderCommand(eventId, List.of(new OrderLine(freeTypeId(eventId), 1)), List.of("Asha")))
                .tickets().get(0);
    }

    private UUID freeTypeId(UUID event) {
        return ticketTypeRepository.findByEventIdOrderByCreatedAtAsc(event).get(0).getId();
    }

    private UUID publishedEventWithFreeTicket() {
        Instant now = Instant.now();
        UUID event = eventService.createDraft(organizerId, new EventDraftCommand(
                CONCERTS, "Scan Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();
        ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), event, "General", null,
                new BigDecimal("0.00"), 50, 4, now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS)));
        eventService.submitForReview(event, organizerId, organizerUserId);
        eventService.approve(event, organizerUserId);
        return event;
    }

    private String scannedToken() {
        return tokenFactory.rawToken(ticket.getId());
    }

    private String codeOf(Throwable thrown) {
        return ((ApiException) thrown).code();
    }

    @Test
    void aScannedTokenResolvesToItsTicket() {
        Ticket found = resolver.resolve(eventId, scannedToken(), null);
        assertThat(found.getId()).isEqualTo(ticket.getId());
    }

    @Test
    void thePublicCodeResolvesToItsTicket() {
        Ticket found = resolver.resolve(eventId, null, ticket.getPublicCode());
        assertThat(found.getId()).isEqualTo(ticket.getId());
    }

    @Test
    void aLowercaseCodeStillResolves() {
        // staff typing by hand should not have to match the case
        Ticket found = resolver.resolve(eventId, null, ticket.getPublicCode().toLowerCase());
        assertThat(found.getId()).isEqualTo(ticket.getId());
    }

    @Test
    void aTokenWithTrailingWhitespaceStillResolves() {
        // some scanners append a newline
        Ticket found = resolver.resolve(eventId, scannedToken() + "\n", null);
        assertThat(found.getId()).isEqualTo(ticket.getId());
    }

    @Test
    void anUnknownTokenIsNotFound() {
        assertThatThrownBy(() -> resolver.resolve(eventId, "not-a-real-token", null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("TICKET_NOT_FOUND"));
    }

    @Test
    void anUnknownCodeIsNotFound() {
        assertThatThrownBy(() -> resolver.resolve(eventId, null, "TCK-0000-0000"))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("TICKET_NOT_FOUND"));
    }

    @Test
    void aRealTicketScannedAtTheWrongEventIsRejectedDistinctly() {
        UUID otherEvent = publishedEventWithFreeTicket();

        assertThatThrownBy(() -> resolver.resolve(otherEvent, scannedToken(), null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("WRONG_EVENT"));
    }

    @Test
    void theWrongEventAnswerAlsoAppliesToThePublicCode() {
        UUID otherEvent = publishedEventWithFreeTicket();

        assertThatThrownBy(() -> resolver.resolve(otherEvent, null, ticket.getPublicCode()))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("WRONG_EVENT"));
    }

    @Test
    void neitherATokenNorACodeIsAValidationError() {
        assertThatThrownBy(() -> resolver.resolve(eventId, "  ", null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void aTokenThatIsNotOursDoesNotMatchByAccident() {
        // a syntactically valid but foreign token must simply miss, not throw something odd
        assertThatThrownBy(() -> resolver.resolve(eventId, tokenFactory.rawToken(UUID.randomUUID()), null))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo("TICKET_NOT_FOUND"));
    }
}
