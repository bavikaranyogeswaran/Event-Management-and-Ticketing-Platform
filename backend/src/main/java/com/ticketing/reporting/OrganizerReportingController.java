package com.ticketing.reporting;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.organizer.OrganizerProfileService;
import com.ticketing.reporting.dto.AttendeeResponse;
import com.ticketing.reporting.dto.EventStatsResponse;
import com.ticketing.reporting.dto.ExportResponse;
import com.ticketing.reporting.dto.OrganizerOrderSummary;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.pagination.PageResponse;
import com.ticketing.shared.pagination.Paging;
import com.ticketing.shared.security.CurrentUser;

@RestController
@RequestMapping("/organizer/events")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerReportingController {

    private static final int ORDERS_DEFAULT = 50;
    private static final int ORDERS_MAX = 100;
    private static final int ATTENDEES_DEFAULT = 50;
    private static final int ATTENDEES_MAX = 100;

    private final ReportingService reportingService;
    private final OrganizerProfileService organizerProfileService;
    private final ExportTriggerService exportTriggerService;

    OrganizerReportingController(ReportingService reportingService,
            OrganizerProfileService organizerProfileService,
            ExportTriggerService exportTriggerService) {
        this.reportingService = reportingService;
        this.organizerProfileService = organizerProfileService;
        this.exportTriggerService = exportTriggerService;
    }

    @GetMapping("/{eventId}/summary")
    EventStatsResponse summary(CurrentUser currentUser, @PathVariable UUID eventId) {
        return reportingService.getEventStats(eventId, organizerId(currentUser));
    }

    @GetMapping("/{eventId}/orders")
    PageResponse<OrganizerOrderSummary> orders(CurrentUser currentUser, @PathVariable UUID eventId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, ORDERS_DEFAULT, ORDERS_MAX);
        List<OrganizerOrderSummary> rows = reportingService.listEventOrders(
                eventId, organizerId(currentUser), KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize, o -> KeysetCursor.encode(o.createdAt(), o.orderId()));
    }

    @GetMapping("/{eventId}/attendees")
    PageResponse<AttendeeResponse> attendees(CurrentUser currentUser, @PathVariable UUID eventId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        int pageSize = Paging.clampLimit(limit, ATTENDEES_DEFAULT, ATTENDEES_MAX);
        List<AttendeeResponse> rows = reportingService.listEventAttendees(
                eventId, organizerId(currentUser), KeysetCursor.decode(cursor), pageSize);
        return PageResponse.of(rows, pageSize, a -> KeysetCursor.encode(a.issuedAt(), a.ticketId()));
    }

    @PostMapping("/{eventId}/attendees/export")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ExportResponse triggerExport(CurrentUser currentUser, @PathVariable UUID eventId) {
        UUID fileId = exportTriggerService.triggerExport(eventId, organizerId(currentUser), currentUser.userId());
        return new ExportResponse(fileId);
    }

    private UUID organizerId(CurrentUser currentUser) {
        return organizerProfileService.getByUser(currentUser.userId()).getId();
    }
}
