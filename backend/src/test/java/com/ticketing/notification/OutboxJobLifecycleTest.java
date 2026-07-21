package com.ticketing.notification;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OutboxJobLifecycleTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    @Autowired
    OutboxJobRepository jobs;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM outbox_jobs");
    }

    private OutboxJob job(String key, Instant dueAt) {
        OutboxJob job = new OutboxJob(UUID.randomUUID(), "EMAIL", key, "{}", dueAt);
        return jobs.saveAndFlush(job);
    }

    @Test
    void aNewJobStartsPendingAndDueNow() {
        OutboxJob saved = job("ORDER_CONFIRMATION:" + UUID.randomUUID(), NOW);

        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
    }

    @Test
    void onlyPendingJobsDueNowAreClaimed() {
        job("a:" + UUID.randomUUID(), NOW.minus(1, ChronoUnit.MINUTES)); // due
        job("b:" + UUID.randomUUID(), NOW.plus(5, ChronoUnit.MINUTES));  // not yet
        OutboxJob sent = job("c:" + UUID.randomUUID(), NOW.minus(1, ChronoUnit.MINUTES));
        sent.markSent(NOW);
        jobs.saveAndFlush(sent);

        var due = jobs.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                OutboxStatus.PENDING, NOW, Limit.of(10));

        assertThat(due).hasSize(1);
    }

    @Test
    void dueJobsComeBackOldestFirst() {
        job("old:" + UUID.randomUUID(), NOW.minus(30, ChronoUnit.MINUTES));
        job("new:" + UUID.randomUUID(), NOW.minus(1, ChronoUnit.MINUTES));

        var due = jobs.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                OutboxStatus.PENDING, NOW, Limit.of(10));

        assertThat(due).extracting(OutboxJob::getJobKey)
                .element(0).asString().startsWith("old:");
    }

    @Test
    void aFailedSendGoesBackToPendingWithABackoffAndAnError() {
        OutboxJob job = job("x:" + UUID.randomUUID(), NOW);
        job.markPublishing();

        job.markForRetry(NOW.plus(5, ChronoUnit.MINUTES), "smtp timeout");
        jobs.saveAndFlush(job);

        OutboxJob reloaded = jobs.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getNextAttemptAt()).isEqualTo(NOW.plus(5, ChronoUnit.MINUTES));
        assertThat(reloaded.getLastError()).isEqualTo("smtp timeout");
    }

    @Test
    void anExhaustedJobBecomesDead() {
        OutboxJob job = job("y:" + UUID.randomUUID(), NOW);

        job.markDead("gave up after 5 tries");
        jobs.saveAndFlush(job);

        OutboxJob reloaded = jobs.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.DEAD);
        // a dead job is never picked up again
        assertThat(jobs.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                OutboxStatus.PENDING, NOW.plus(1, ChronoUnit.DAYS), Limit.of(10))).isEmpty();
    }

    @Test
    void aStalePublishingJobIsFoundForRecovery() {
        OutboxJob job = job("z:" + UUID.randomUUID(), NOW);
        job.markPublishing();
        jobs.saveAndFlush(job);
        // simulate the claim happening well in the past
        jdbc.update("UPDATE outbox_jobs SET updated_at = now() - interval '10 minutes' WHERE id = ?", job.getId());

        var stale = jobs.findByStatusAndUpdatedAtLessThan(
                OutboxStatus.PUBLISHING, Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThat(stale).extracting(OutboxJob::getId).contains(job.getId());
    }

    @Test
    void recoveringAStuckJobDoesNotSpendAnAttempt() {
        OutboxJob job = job("r:" + UUID.randomUUID(), NOW);
        job.markPublishing();

        job.resetToPending();
        jobs.saveAndFlush(job);

        OutboxJob reloaded = jobs.findById(job.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(reloaded.getAttempts()).isZero();
    }

    @Test
    void jobsCanBeCountedByStatus() {
        job("p1:" + UUID.randomUUID(), NOW);
        job("p2:" + UUID.randomUUID(), NOW);
        OutboxJob dead = job("d1:" + UUID.randomUUID(), NOW);
        dead.markDead("nope");
        jobs.saveAndFlush(dead);

        assertThat(jobs.countByStatus(OutboxStatus.PENDING)).isEqualTo(2);
        assertThat(jobs.countByStatus(OutboxStatus.DEAD)).isEqualTo(1);
    }
}
