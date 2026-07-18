package com.ticketing.tickettype;

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
import com.ticketing.event.EventDraftCommand;
import com.ticketing.event.EventService;
import com.ticketing.event.EventType;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class TicketTypeInventoryTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    TicketTypeRepository ticketTypeRepository;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;

    private UUID eventId;

    @BeforeEach
    void setUpEvent() {
        User organizerUser = userRepository.saveAndFlush(
                new User(UUID.randomUUID(), "org." + UUID.randomUUID() + "@example.com", "hash", "Organizer"));
        OrganizerProfile profile = organizerProfileRepository.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUser.getId(), "Test Org", null, null));

        Instant now = Instant.now();
        eventId = eventService.createDraft(profile.getId(), new EventDraftCommand(
                CONCERTS, "Inventory Event", null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                now.plus(30, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                now, now.plus(29, ChronoUnit.DAYS), 500)).getId();
    }

    private TicketType ticketType(int quantityTotal, int quantitySold, TicketTypeStatus status) {
        Instant now = Instant.now();
        TicketType type = new TicketType(UUID.randomUUID(), eventId, "General", null, new BigDecimal("0.00"),
                quantityTotal, 10, now, now.plus(28, ChronoUnit.DAYS));
        type.setStatus(status);
        ticketTypeRepository.saveAndFlush(type);
        if (quantitySold > 0) {
            jdbc.update("UPDATE ticket_types SET quantity_sold = ? WHERE id = ?", quantitySold, type.getId());
        }
        return type;
    }

    // the reserve statement bypasses the persistence context, so always read the counter fresh
    private int soldOf(UUID ticketTypeId) {
        entityManager.clear();
        return ticketTypeRepository.findById(ticketTypeId).orElseThrow().getQuantitySold();
    }

    @Test
    void reserveClaimsRequestedQuantity() {
        TicketType type = ticketType(100, 0, TicketTypeStatus.ACTIVE);
        assertThat(ticketTypeRepository.reserve(type.getId(), 3)).isEqualTo(1);
        assertThat(soldOf(type.getId())).isEqualTo(3);
    }

    @Test
    void reserveUpToExactCapacitySucceeds() {
        TicketType type = ticketType(10, 0, TicketTypeStatus.ACTIVE);
        assertThat(ticketTypeRepository.reserve(type.getId(), 10)).isEqualTo(1);
        assertThat(soldOf(type.getId())).isEqualTo(10);
    }

    @Test
    void reserveBeyondRemainingStockChangesNothing() {
        TicketType type = ticketType(10, 8, TicketTypeStatus.ACTIVE);
        assertThat(ticketTypeRepository.reserve(type.getId(), 3)).isZero();
        assertThat(soldOf(type.getId())).isEqualTo(8);
    }

    @Test
    void reserveOnInactiveTicketTypeChangesNothing() {
        TicketType type = ticketType(100, 0, TicketTypeStatus.INACTIVE);
        assertThat(ticketTypeRepository.reserve(type.getId(), 1)).isZero();
        assertThat(soldOf(type.getId())).isZero();
    }

    @Test
    void secondReserveFailsOnceStockIsGone() {
        TicketType type = ticketType(10, 0, TicketTypeStatus.ACTIVE);
        assertThat(ticketTypeRepository.reserve(type.getId(), 6)).isEqualTo(1);
        assertThat(ticketTypeRepository.reserve(type.getId(), 6)).isZero();
        assertThat(soldOf(type.getId())).isEqualTo(6);
    }
}
