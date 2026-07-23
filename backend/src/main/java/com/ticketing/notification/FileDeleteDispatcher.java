package com.ticketing.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.file.ObjectStorage;
import com.ticketing.shared.config.AppProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * Delivers one FILE_DELETE job: reads the public ID from the payload, destroys the Cloudinary copy,
 * and records the outcome; follows the same pessimistic-lock + retry pattern as EmailDispatcher.
 */
@Service
class FileDeleteDispatcher {

    private final OutboxJobRepository jobs;
    private final Optional<ObjectStorage> storage;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final List<Duration> backoff;

    FileDeleteDispatcher(OutboxJobRepository jobs, Optional<ObjectStorage> storage,
            Clock clock, ObjectMapper objectMapper, AppProperties properties) {
        this.jobs = jobs;
        this.storage = storage;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.backoff = properties.messaging().retryBackoff();
    }

    @Transactional
    void deliver(UUID jobId) {
        OutboxJob job = jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("No outbox job " + jobId));
        if (job.getStatus() != OutboxStatus.PUBLISHING) {
            return; // duplicate delivery or the reaper already moved it on
        }
        ObjectStorage provider = storage
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("Object storage is not configured"));
        try {
            FileDeletePayload payload = objectMapper.readValue(job.getPayload(), FileDeletePayload.class);
            provider.destroy(payload.publicId());
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

    // matches FileDeleteService's local FileDeletePayload on JSON field names only
    private record FileDeletePayload(String publicId) {
    }
}
