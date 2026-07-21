package com.ticketing.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The database side of the relay: claiming due jobs, and handing them back when a publish fails or stalls. */
@Service
class OutboxClaimer {

    private final OutboxJobRepository jobs;

    OutboxClaimer(OutboxJobRepository jobs) {
        this.jobs = jobs;
    }

    /**
     * Marks a batch of due jobs PUBLISHING and commits before anything is published, so the
     * consumer only ever meets a job that has already been claimed, never one mid-claim.
     */
    @Transactional
    List<OutboxJob> claimDue(Instant now, int batchSize) {
        List<OutboxJob> due = jobs.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                OutboxStatus.PENDING, now, Limit.of(batchSize));
        due.forEach(OutboxJob::markPublishing);
        return due;
    }

    /** A publish never reached the broker: return the job to the queue without spending a retry. */
    @Transactional
    void releaseForRetry(UUID jobId) {
        jobs.findById(jobId).ifPresent(OutboxJob::resetToPending);
    }

    /** Reclaims jobs left PUBLISHING by a crash between claim and publish. */
    @Transactional
    int recoverStale(Instant cutoff) {
        List<OutboxJob> stale = jobs.findByStatusAndUpdatedAtLessThan(OutboxStatus.PUBLISHING, cutoff);
        stale.forEach(OutboxJob::resetToPending);
        return stale.size();
    }
}
