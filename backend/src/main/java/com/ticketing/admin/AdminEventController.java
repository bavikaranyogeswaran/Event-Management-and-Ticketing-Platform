package com.ticketing.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.admin.dto.ReviewRequest;
import com.ticketing.event.Event;
import com.ticketing.event.EventService;
import com.ticketing.event.EventStatus;
import com.ticketing.event.dto.EventDetailResponse;
import com.ticketing.event.dto.EventSummaryResponse;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.PageResponse;
import com.ticketing.shared.pagination.Paging;
import com.ticketing.shared.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/admin/events")
@PreAuthorize("hasRole('ADMIN')")
class AdminEventController {

    private static final int DEFAULT_PAGE = 25;
    private static final int MAX_PAGE = 100;

    private final EventService eventService;

    AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    PageResponse<EventSummaryResponse> list(@RequestParam(required = false) EventStatus status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, DEFAULT_PAGE, MAX_PAGE);
        List<Event> rows = eventService.listAdminEvents(status, KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize, e -> KeysetCursor.encode(e.getCreatedAt(), e.getId()))
                .map(EventSummaryResponse::from);
    }

    @GetMapping("/{eventId}")
    EventDetailResponse get(@PathVariable UUID eventId) {
        Event event = eventService.getEvent(eventId);
        return EventDetailResponse.from(event, eventService.getTicketTypes(eventId));
    }

    @PostMapping("/{eventId}/review")
    EventDetailResponse review(CurrentUser currentUser, @PathVariable UUID eventId,
            @Valid @RequestBody ReviewRequest request) {
        Event event = eventService.review(eventId, currentUser.userId(), request.decision(), request.reason());
        return EventDetailResponse.from(event, eventService.getTicketTypes(eventId));
    }
}
