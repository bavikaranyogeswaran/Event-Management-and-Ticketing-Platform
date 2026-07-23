package com.ticketing.reporting;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.audit.AuditActions;
import com.ticketing.audit.AuditService;
import com.ticketing.file.FileAssetRepository;
import com.ticketing.file.ObjectStorage;
import com.ticketing.notification.OutboxJob;
import com.ticketing.notification.OutboxJobRepository;
import com.ticketing.notification.OutboxStatus;
import com.ticketing.reporting.dto.AttendeeResponse;
import com.ticketing.shared.config.AppProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Delivers one EXPORT job: fetches all attendees, writes a CSV, uploads it as a private raw file,
 * marks the file_asset READY, and audits — all in the same transaction as the job lock.
 */
@Service
class CsvExportDispatcher {

    private final OutboxJobRepository jobs;
    private final ReportingService reportingService;
    private final Optional<ObjectStorage> storage;
    private final FileAssetRepository fileAssets;
    private final AuditService auditService;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final List<Duration> backoff;

    CsvExportDispatcher(OutboxJobRepository jobs, ReportingService reportingService,
            Optional<ObjectStorage> storage, FileAssetRepository fileAssets,
            AuditService auditService, Clock clock, ObjectMapper objectMapper,
            AppProperties properties) {
        this.jobs = jobs;
        this.reportingService = reportingService;
        this.storage = storage;
        this.fileAssets = fileAssets;
        this.auditService = auditService;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.backoff = properties.messaging().retryBackoff();
    }

    @Transactional
    void deliver(UUID jobId) {
        OutboxJob job = jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("No outbox job " + jobId));
        if (job.getStatus() != OutboxStatus.PUBLISHING) {
            return;
        }
        ObjectStorage provider = storage
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("Object storage is not configured"));
        try {
            ExportJobPayload p = objectMapper.readValue(job.getPayload(), ExportJobPayload.class);
            List<AttendeeResponse> attendees = reportingService.listAllEventAttendees(p.eventId(), p.organizerId());
            byte[] csv = buildCsv(attendees);
            provider.uploadRaw(p.publicId(), csv, "text/csv");
            fileAssets.findById(p.fileId()).ifPresent(a -> a.markReady("text/csv", csv.length));
            auditService.record(AuditActions.EXPORT_GENERATED, p.ownerUserId(), "FILE_ASSET", p.fileId(), null);
            job.markSent(Instant.now(clock));
        } catch (RuntimeException e) {
            reschedule(job, e);
        }
    }

    private void reschedule(OutboxJob job, RuntimeException cause) {
        int attempt = job.getAttempts();
        if (attempt < backoff.size()) {
            job.markForRetry(Instant.now(clock).plus(backoff.get(attempt)), summarize(cause));
        } else {
            job.markDead(summarize(cause));
        }
    }

    private String summarize(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static byte[] buildCsv(List<AttendeeResponse> rows) {
        var sb = new StringBuilder("Ticket ID,Code,Attendee,Ticket Type,Status,Issued At,Checked In At\n");
        for (AttendeeResponse r : rows) {
            sb.append(escape(r.ticketId().toString())).append(',')
              .append(escape(r.publicCode())).append(',')
              .append(escape(r.attendeeName())).append(',')
              .append(escape(r.ticketTypeName())).append(',')
              .append(escape(r.status())).append(',')
              .append(r.issuedAt()).append(',')
              .append(r.checkedInAt() != null ? r.checkedInAt() : "").append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    // field names match ExportTriggerService's local ExportJobPayload on JSON field names
    private record ExportJobPayload(UUID fileId, String publicId, UUID eventId, UUID organizerId, UUID ownerUserId) {
    }
}
