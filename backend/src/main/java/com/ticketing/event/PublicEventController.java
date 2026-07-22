package com.ticketing.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.event.dto.EventSummaryResponse;
import com.ticketing.event.dto.PublicEventResponse;
import com.ticketing.event.dto.TicketTypeResponse;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.PageResponse;
import com.ticketing.shared.pagination.Paging;

@RestController
@RequestMapping("/events")
class PublicEventController {

    private static final int DEFAULT_PAGE = 20;
    private static final int MAX_PAGE = 50;

    private final EventService eventService;

    PublicEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    PageResponse<EventSummaryResponse> search(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, DEFAULT_PAGE, MAX_PAGE);
        PublicEventFilter filter = new PublicEventFilter(categoryId, from, to, q);
        List<Event> rows = eventService.searchPublicEvents(filter, KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize, e -> KeysetCursor.encode(e.getStartsAt(), e.getId()))
                .map(EventSummaryResponse::from);
    }

    @GetMapping("/{eventId}")
    PublicEventResponse get(@PathVariable UUID eventId) {
        Event event = eventService.getPublicEvent(eventId);
        return PublicEventResponse.from(event, eventService.getPublicTicketTypes(eventId),
                eventService.bannerUrl(event.getBannerFileId()));
    }

    @GetMapping("/slug/{slug}")
    PublicEventResponse getBySlug(@PathVariable String slug) {
        Event event = eventService.getPublicEventBySlug(slug);
        return PublicEventResponse.from(event, eventService.getPublicTicketTypes(event.getId()),
                eventService.bannerUrl(event.getBannerFileId()));
    }

    @GetMapping("/{eventId}/ticket-types")
    List<TicketTypeResponse> ticketTypes(@PathVariable UUID eventId) {
        return eventService.getPublicTicketTypes(eventId).stream().map(TicketTypeResponse::from).toList();
    }
}
