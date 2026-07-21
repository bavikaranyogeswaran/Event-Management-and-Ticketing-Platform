package com.ticketing.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketing.shared.config.AppProperties;

/** Moves due jobs from the outbox onto RabbitMQ, and rescues any left stuck mid-publish. */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxClaimer claimer;
    private final OutboxPublisher publisher;
    private final Clock clock;
    private final int batchSize;
    private final Duration recoveryGrace;

    OutboxRelay(OutboxClaimer claimer, OutboxPublisher publisher, Clock clock, AppProperties properties) {
        this.claimer = claimer;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = properties.messaging().batchSize();
        this.recoveryGrace = properties.messaging().recoveryGrace();
    }

    @Scheduled(fixedDelayString = "${app.messaging.relay-interval}")
    void scheduledPublish() {
        publishDueJobs();
    }

    @Scheduled(fixedDelayString = "${app.messaging.recovery-interval}")
    void scheduledRecovery() {
        recoverStuckJobs();
    }

    /** Publishes one batch of due jobs; a job that fails to reach the broker waits for the next run. */
    int publishDueJobs() {
        List<OutboxJob> claimed = claimer.claimDue(Instant.now(clock), batchSize);
        int published = 0;
        for (OutboxJob job : claimed) {
            try {
                publisher.publish(job.getId());
                published++;
            } catch (RuntimeException e) {
                // the broker was unreachable; hand the job back so a later run tries again
                log.warn("Could not publish outbox job {}: {}", job.getId(), e.getMessage());
                claimer.releaseForRetry(job.getId());
            }
        }
        return published;
    }

    /** Returns jobs stranded PUBLISHING by a crash back to PENDING so they are published again. */
    int recoverStuckJobs() {
        Instant cutoff = Instant.now(clock).minus(recoveryGrace);
        int recovered = claimer.recoverStale(cutoff);
        if (recovered > 0) {
            log.warn("Recovered {} outbox job(s) stuck mid-publish", recovered);
        }
        return recovered;
    }
}
