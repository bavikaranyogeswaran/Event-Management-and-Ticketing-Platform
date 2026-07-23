package com.ticketing.reporting;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.EventService;
import com.ticketing.file.FileService;
import com.ticketing.file.dto.ExportAsset;
import com.ticketing.notification.JobTypes;
import com.ticketing.notification.OutboxJobService;

/** Creates the file_asset record and enqueues the export job in a single transaction. */
@Service
class ExportTriggerService {

    private final EventService eventService;
    private final FileService fileService;
    private final OutboxJobService outboxJobService;

    ExportTriggerService(EventService eventService, FileService fileService, OutboxJobService outboxJobService) {
        this.eventService = eventService;
        this.fileService = fileService;
        this.outboxJobService = outboxJobService;
    }

    @Transactional
    public UUID triggerExport(UUID eventId, UUID organizerId, UUID ownerUserId) {
        eventService.getOwnedEvent(eventId, organizerId); // 404 if not owned
        ExportAsset asset = fileService.createExportRecord(ownerUserId);
        outboxJobService.enqueue(JobTypes.EXPORT, JobTypes.exportKey(asset.fileId()),
                new ExportJobPayload(asset.fileId(), asset.publicId(), eventId, organizerId, ownerUserId));
        return asset.fileId();
    }

    // The consumer in Step 12.8 re-declares this record locally and relies on JSON field name matching.
    private record ExportJobPayload(UUID fileId, String publicId, UUID eventId, UUID organizerId, UUID ownerUserId) {
    }
}
