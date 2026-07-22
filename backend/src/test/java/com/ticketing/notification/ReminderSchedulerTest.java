package com.ticketing.notification;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import com.ticketing.ticket.TicketRepository;
import com.ticketing.tickettype.TicketType;
import com.ticketing.tickettype.TicketTypeRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ReminderSchedulerTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Autowired
    ReminderScheduler scheduler;
    @Autowired
    ReminderEnqueuer enqueuer;
    @Autowired
    EventService eventService;
    @Autowired
    OrderService orderService;
    @Autowired
    TicketTypeRepository ticketTypes;
    @Autowired
    TicketRepository tickets;
    @Autowired
    UserRepository users;
    @Autowired
    OrganizerProfileRepository organizerProfiles;
    @Autowired
    OutboxJobRepository jobs;

    private UUID organizerUserId;
    private UUID organizerProfileId;

    @BeforeEach
    void setUp() {
        organizerUserId = user("Organizer");
        organizerProfileId = organizerProfiles.saveAndFlush(
                new OrganizerProfile(UUID.randomUUID(), organizerUserId, "Org", null, null)).getId();
    }

    private UUID user(String name) {
        return users.saveAndFlush(new User(UUID.randomUUID(),
                name + "." + UUID.randomUUID() + "@example.com", "hash", name)).getId();
    }

    // a published event with a free ticket type on sale, starting the given distance from now
    private UUID publishedEventStartingIn(Duration untilStart) {
        Instant now = Instant.now();
        Instant starts = now.plus(untilStart);
        UUID eventId = eventService.createDraft(organizerProfileId, new EventDraftCommand(
                CONCERTS, "Event " + UUID.randomUUID(), null, EventType.PHYSICAL,
                "Trace Expert City", "Maradana Rd", "Colombo", null, "Asia/Colombo",
                starts, starts.plus(3, ChronoUnit.HOURS),
                now.minus(1, ChronoUnit.HOURS), starts.minus(1, ChronoUnit.HOURS), 500)).getId();
        ticketTypes.saveAndFlush(new TicketType(UUID.randomUUID(), eventId, "General", null,
                new BigDecimal("0.00"), 50, 10, now.minus(1, ChronoUnit.HOURS), starts.minus(1, ChronoUnit.HOURS)));
        eventService.submitForReview(eventId, organizerProfileId, organizerUserId);
        eventService.approve(eventId, organizerUserId);
        return eventId;
    }

    private void issueTickets(UUID eventId, UUID buyerId, int count) {
        UUID typeId = ticketTypes.findByEventIdOrderByCreatedAtAsc(eventId).get(0).getId();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add("Guest " + (i + 1));
        }
        orderService.place(buyerId, UUID.randomUUID().toString(),
                new OrderCommand(eventId, List.of(new OrderLine(typeId, count)), names));
    }

    private boolean queued(UUID eventId, UUID holderId) {
        return jobs.findByJobKey(JobTypes.reminderKey(eventId, holderId)).isPresent();
    }

    @Test
    void onlyHoldersOfEventsInsideTheWindowWithValidTicketsAreReminded() {
        UUID soon = publishedEventStartingIn(Duration.ofHours(12)); // inside 24h
        UUID here = user("Here");
        issueTickets(soon, here, 1);

        UUID later = publishedEventStartingIn(Duration.ofHours(40)); // outside 24h
        UUID far = user("Far");
        issueTickets(later, far, 1);

        UUID cancelledOnly = publishedEventStartingIn(Duration.ofHours(6)); // inside, but no valid holder
        UUID gone = user("Gone");
        issueTickets(cancelledOnly, gone, 1);
        tickets.cancelValidTicketsForEvent(cancelledOnly, Instant.now());

        scheduler.enqueueDueReminders();

        assertThat(queued(soon, here)).isTrue();
        assertThat(queued(later, far)).isFalse();
        assertThat(queued(cancelledOnly, gone)).isFalse();
    }

    @Test
    void aHolderIsRemindedOncePerEventEvenAcrossSweepsAndExtraTickets() {
        UUID event = publishedEventStartingIn(Duration.ofHours(12));
        UUID one = user("One");
        UUID two = user("Two");
        issueTickets(event, one, 2); // two tickets, one holder
        issueTickets(event, two, 1);

        int first = enqueuer.enqueueForEvent(event);
        int second = enqueuer.enqueueForEvent(event);

        assertThat(first).isEqualTo(2); // one job each, not one per ticket
        assertThat(second).isZero(); // a repeat sweep adds nothing
        assertThat(queued(event, one)).isTrue();
        assertThat(queued(event, two)).isTrue();
    }
}
