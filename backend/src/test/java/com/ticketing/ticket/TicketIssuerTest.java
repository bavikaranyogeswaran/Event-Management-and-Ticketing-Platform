package com.ticketing.ticket;

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
import com.ticketing.order.OrderResult;
import com.ticketing.order.OrderService;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.shared.security.TokenService;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TicketIssuerTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    OrderService orderService;
    @Autowired
    TicketTokenFactory ticketTokens;
    @Autowired
    TokenService tokenService;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    TicketTypeRepository ticketTypeRepository;

    private UUID buyerId;
    private UUID eventId;
    private UUID freeTypeId;

    @BeforeEach
    void setUp() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        buyerId = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "buyer." + UUID.randomUUID() + "@example.com", "hash", "Buyer")).getId();
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, "Issuer Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.DAYS), now.plus(29, ChronoUnit.DAYS), 500)).getId();

        freeTypeId = ticketTypeRepository.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 20, 4,
                now.minus(1, ChronoUnit.DAYS), now.plus(28, ChronoUnit.DAYS))).getId();

        eventService.submitForReview(eventId, profile.getId(), organizerUser.getId());
        eventService.approve(eventId, organizerUser.getId());
    }

    private OrderResult place(String key, List<String> attendees) {
        return orderService.place(buyerId, key, new OrderCommand(eventId,
                List.of(new OrderLine(freeTypeId, attendees.size())), attendees));
    }

    @Test
    void anIssuedTicketsQrTokenCanBeRebuiltLater() {
        Ticket issued = place("qr-1", List.of("Asha")).tickets().get(0);

        // exactly what rendering a QR later, or hashing a scanned one at check-in, produces
        String rebuilt = ticketTokens.rawToken(issued.getId());
        assertThat(tokenService.hash(rebuilt)).isEqualTo(issued.getValidationTokenHash());
    }

    @Test
    void everyTicketInAnOrderGetsItsOwnToken() {
        List<Ticket> issued = place("qr-2", List.of("Asha", "Nuwan", "Kamal")).tickets();

        assertThat(issued).extracting(Ticket::getValidationTokenHash).doesNotHaveDuplicates();
        assertThat(issued).allSatisfy(ticket ->
                assertThat(tokenService.hash(ticketTokens.rawToken(ticket.getId())))
                        .isEqualTo(ticket.getValidationTokenHash()));
    }

    @Test
    void theStoredValueIsAHashAndNotTheTokenItself() {
        Ticket issued = place("qr-3", List.of("Asha")).tickets().get(0);

        assertThat(issued.getValidationTokenHash())
                .isNotEqualTo(ticketTokens.rawToken(issued.getId()))
                .matches("[0-9a-f]{64}");
    }
}
