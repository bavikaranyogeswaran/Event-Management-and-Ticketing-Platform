package com.ticketing.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.config.AppProperties;

/**
 * Delivers one claimed job: render, send, and record the outcome in the same transaction as the
 * row lock, so a job is sent at most once and every failure decides its own next attempt.
 */
@Service
class EmailDispatcher {

    private final OutboxJobRepository jobs;
    private final EmailContentFactory content;
    private final EmailSender emailSender;
    private final Clock clock;
    private final List<Duration> backoff;

    EmailDispatcher(OutboxJobRepository jobs, EmailContentFactory content, EmailSender emailSender,
            Clock clock, AppProperties properties) {
        this.jobs = jobs;
        this.content = content;
        this.emailSender = emailSender;
        this.clock = clock;
        this.backoff = properties.messaging().retryBackoff();
    }

    @Transactional
    void deliver(UUID jobId) {
        OutboxJob job = jobs.findByIdForUpdate(jobId)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("No outbox job " + jobId));
        if (job.getStatus() != OutboxStatus.PUBLISHING) {
            return; // a duplicate delivery, or the reaper already moved it on: send once, guarded by state
        }
        try {
            emailSender.send(content.render(job.getJobKey(), job.getPayload()));
            job.markSent(Instant.now(clock));
        } catch (RuntimeException e) {
            reschedule(job, e);
        }
    }

    /** Waits out the next backoff step, or gives up once the steps are spent. */
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
}
