package com.ticketing.reporting;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.organizer.OrganizerProfileService;
import com.ticketing.reporting.dto.EventStatsResponse;
import com.ticketing.shared.security.CurrentUser;

@RestController
@RequestMapping("/organizer/events")
@PreAuthorize("hasRole('ORGANIZER')")
class OrganizerReportingController {

    private final ReportingService reportingService;
    private final OrganizerProfileService organizerProfileService;

    OrganizerReportingController(ReportingService reportingService,
            OrganizerProfileService organizerProfileService) {
        this.reportingService = reportingService;
        this.organizerProfileService = organizerProfileService;
    }

    @GetMapping("/{eventId}/summary")
    EventStatsResponse summary(CurrentUser currentUser, @PathVariable UUID eventId) {
        return reportingService.getEventStats(eventId, organizerId(currentUser));
    }

    private UUID organizerId(CurrentUser currentUser) {
        return organizerProfileService.getByUser(currentUser.userId()).getId();
    }
}
