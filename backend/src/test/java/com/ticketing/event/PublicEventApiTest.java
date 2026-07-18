package com.ticketing.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;
import com.ticketing.organizer.OrganizerProfile;
import com.ticketing.organizer.OrganizerProfileRepository;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class PublicEventApiTest extends AbstractIntegrationTest {

    private static final UUID CONCERTS = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID WORKSHOPS = UUID.fromString("c0000000-0000-4000-8000-000000000002");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EventService eventService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    OrganizerProfileRepository organizerProfileRepository;

    private UUID organizerId;
    private UUID adminUserId;

    private UUID organizer() {
        if (organizerId == null) {
            User u = userRepository.saveAndFlush(
                    new User(UUID.randomUUID(), "o." + UUID.randomUUID() + "@e.com", "h", "Org"));
            organizerId = organizerProfileRepository.saveAndFlush(
                    new OrganizerProfile(UUID.randomUUID(), u.getId(), "Org", null, null)).getId();
            adminUserId = userRepository.saveAndFlush(
                    new User(UUID.randomUUID(), "a." + UUID.randomUUID() + "@e.com", "h", "Admin")).getId();
        }
        return organizerId;
    }

    private Event draft(String title, UUID category, Instant start, Instant end) {
        // registration window sits entirely before the event start, whether start is past or future
        return eventService.createDraft(organizer(), new EventDraftCommand(category, title, "desc",
                EventType.PHYSICAL, "Venue", "Addr", "Colombo", null, "Asia/Colombo",
                start, end, start.minus(10, ChronoUnit.DAYS), start.minus(1, ChronoUnit.HOURS), 100));
    }

    private Event publish(String title, UUID category, Instant start, Instant end) {
        Event event = draft(title, category, start, end);
        eventService.addTicketType(event.getId(), organizer(), new TicketTypeCommand("GA", null,
                new BigDecimal("1000.00"), 50, 4, start.minus(10, ChronoUnit.DAYS), start.minus(1, ChronoUnit.HOURS)));
        eventService.submitForReview(event.getId(), organizer(), adminUserId);
        eventService.approve(event.getId(), adminUserId);
        return event;
    }

    @Test
    void listsPublishedUpcomingEventWithoutAuth() throws Exception {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = publish("Colombo Jazz Night", CONCERTS, start, start.plus(3, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + event.getId() + "')]").exists());
    }

    @Test
    void directLinkReturnsDetailWithTicketTypes() throws Exception {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event event = publish("Detailed Event", CONCERTS, start, start.plus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/events/" + event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(event.getSlug()))
                .andExpect(jsonPath("$.ticketTypes.length()").value(1));

        mockMvc.perform(get("/api/v1/events/slug/" + event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.getId().toString()));
    }

    @Test
    void draftIsNeverPubliclyVisible() throws Exception {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event unpublished = draft("Secret Draft", CONCERTS, start, start.plus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/events/" + unpublished.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events"))
                .andExpect(jsonPath("$.items[?(@.id == '" + unpublished.getId() + "')]").doesNotExist());
    }

    @Test
    void finishedEventDropsFromListButOpensByLink() throws Exception {
        Instant start = Instant.now().minus(5, ChronoUnit.DAYS);
        Event past = publish("Last Week Gig", CONCERTS, start, start.plus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(jsonPath("$.items[?(@.id == '" + past.getId() + "')]").doesNotExist());
        mockMvc.perform(get("/api/v1/events/" + past.getId()))
                .andExpect(status().isOk());
    }

    @Test
    void filtersByCategoryAndTitle() throws Exception {
        Instant start = Instant.now().plus(10, ChronoUnit.DAYS);
        Event concert = publish("Rock Concert", CONCERTS, start, start.plus(2, ChronoUnit.HOURS));
        Event workshop = publish("Pottery Workshop", WORKSHOPS, start, start.plus(2, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/events?categoryId=" + WORKSHOPS))
                .andExpect(jsonPath("$.items[?(@.id == '" + workshop.getId() + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id == '" + concert.getId() + "')]").doesNotExist());

        mockMvc.perform(get("/api/v1/events?q=pottery"))
                .andExpect(jsonPath("$.items[?(@.id == '" + workshop.getId() + "')]").exists())
                .andExpect(jsonPath("$.items[?(@.id == '" + concert.getId() + "')]").doesNotExist());
    }
}
