package com.ticketing.event;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.event.dto.AssignStaffRequest;
import com.ticketing.event.dto.EventDetailResponse;
import com.ticketing.event.dto.EventStaffResponse;
import com.ticketing.event.dto.EventRequest;
import com.ticketing.event.dto.EventSummaryResponse;
import com.ticketing.event.dto.TicketTypeRequest;
import com.ticketing.event.dto.TicketTypeResponse;
import com.ticketing.organizer.OrganizerProfileService;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.PageResponse;
import com.ticketing.shared.pagination.Paging;
import com.ticketing.shared.security.CurrentUser;
import com.ticketing.tickettype.TicketType;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/organizer/events")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerEventController {

    private static final int DEFAULT_PAGE = 20;
    private static final int MAX_PAGE = 50;

    private final EventService eventService;
    private final EventStaffService eventStaffService;
    private final OrganizerProfileService organizerProfileService;

    OrganizerEventController(EventService eventService, EventStaffService eventStaffService,
            OrganizerProfileService organizerProfileService) {
        this.eventStaffService = eventStaffService;
        this.eventService = eventService;
        this.organizerProfileService = organizerProfileService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EventDetailResponse create(CurrentUser currentUser, @Valid @RequestBody EventRequest request) {
        Event event = eventService.createDraft(organizerId(currentUser), request.toCommand());
        return EventDetailResponse.from(event, List.of());
    }

    @GetMapping
    PageResponse<EventSummaryResponse> list(CurrentUser currentUser,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, DEFAULT_PAGE, MAX_PAGE);
        List<Event> rows = eventService.listOrganizerEvents(
                organizerId(currentUser), KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize, e -> KeysetCursor.encode(e.getCreatedAt(), e.getId()))
                .map(EventSummaryResponse::from);
    }

    @GetMapping("/{eventId}")
    EventDetailResponse get(CurrentUser currentUser, @PathVariable UUID eventId) {
        UUID organizerId = organizerId(currentUser);
        Event event = eventService.getOwnedEvent(eventId, organizerId);
        return EventDetailResponse.from(event, eventService.listTicketTypes(eventId, organizerId));
    }

    @PatchMapping("/{eventId}")
    EventDetailResponse update(CurrentUser currentUser, @PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request) {
        UUID organizerId = organizerId(currentUser);
        Event event = eventService.updateEvent(eventId, organizerId, request.toCommand());
        return EventDetailResponse.from(event, eventService.listTicketTypes(eventId, organizerId));
    }

    @PostMapping("/{eventId}/submit")
    EventDetailResponse submit(CurrentUser currentUser, @PathVariable UUID eventId) {
        UUID organizerId = organizerId(currentUser);
        Event event = eventService.submitForReview(eventId, organizerId, currentUser.userId());
        return EventDetailResponse.from(event, eventService.listTicketTypes(eventId, organizerId));
    }

    @PostMapping("/{eventId}/withdraw")
    EventDetailResponse withdraw(CurrentUser currentUser, @PathVariable UUID eventId) {
        UUID organizerId = organizerId(currentUser);
        Event event = eventService.withdraw(eventId, organizerId);
        return EventDetailResponse.from(event, eventService.listTicketTypes(eventId, organizerId));
    }

    @PostMapping("/{eventId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(CurrentUser currentUser, @PathVariable UUID eventId) {
        eventService.cancelByOrganizer(eventId, organizerId(currentUser), currentUser.userId());
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(CurrentUser currentUser, @PathVariable UUID eventId) {
        eventService.softDeleteDraft(eventId, organizerId(currentUser));
    }

    @PostMapping("/{eventId}/ticket-types")
    @ResponseStatus(HttpStatus.CREATED)
    TicketTypeResponse addTicketType(CurrentUser currentUser, @PathVariable UUID eventId,
            @Valid @RequestBody TicketTypeRequest request) {
        TicketType ticketType = eventService.addTicketType(eventId, organizerId(currentUser), request.toCommand());
        return TicketTypeResponse.from(ticketType);
    }

    @PatchMapping("/{eventId}/ticket-types/{ticketTypeId}")
    TicketTypeResponse updateTicketType(CurrentUser currentUser, @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId, @Valid @RequestBody TicketTypeRequest request) {
        TicketType ticketType = eventService.updateTicketType(
                eventId, ticketTypeId, organizerId(currentUser), request.toCommand());
        return TicketTypeResponse.from(ticketType);
    }

    @GetMapping("/{eventId}/ticket-types")
    List<TicketTypeResponse> listTicketTypes(CurrentUser currentUser, @PathVariable UUID eventId) {
        return eventService.listTicketTypes(eventId, organizerId(currentUser)).stream()
                .map(TicketTypeResponse::from).toList();
    }

    @PostMapping("/{eventId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    EventStaffResponse assignStaff(CurrentUser currentUser, @PathVariable UUID eventId,
            @Valid @RequestBody AssignStaffRequest request) {
        return EventStaffResponse.from(eventStaffService.assign(
                eventId, organizerId(currentUser), currentUser.userId(), request.email()));
    }

    @GetMapping("/{eventId}/staff")
    List<EventStaffResponse> listStaff(CurrentUser currentUser, @PathVariable UUID eventId) {
        return eventStaffService.list(eventId, organizerId(currentUser)).stream()
                .map(EventStaffResponse::from).toList();
    }

    @DeleteMapping("/{eventId}/staff/{staffUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeStaff(CurrentUser currentUser, @PathVariable UUID eventId, @PathVariable UUID staffUserId) {
        eventStaffService.remove(eventId, organizerId(currentUser), currentUser.userId(), staffUserId);
    }

    private UUID organizerId(CurrentUser currentUser) {
        return organizerProfileService.getByUser(currentUser.userId()).getId();
    }
}
