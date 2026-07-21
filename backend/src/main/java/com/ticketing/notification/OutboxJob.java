package com.ticketing.notification;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.ticketing.shared.jpa.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A queued side effect (email, export, cleanup) written in the same transaction as its business change. */
@Entity
@Table(name = "outbox_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxJob extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "job_type", nullable = false)
    private String jobType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    // blocks the same logical job from being enqueued twice
    @Column(name = "job_key", nullable = false)
    private String jobKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    public OutboxJob(UUID id, String jobType, String jobKey, String payload, Instant now) {
        this.id = id;
        this.jobType = jobType;
        this.jobKey = jobKey;
        this.payload = payload;
        this.nextAttemptAt = now;
    }

    /** Claimed by the relay; a second poll will skip it while it is in flight. */
    public void markPublishing() {
        this.status = OutboxStatus.PUBLISHING;
    }

    public void markSent(Instant now) {
        this.status = OutboxStatus.SENT;
        this.sentAt = now;
    }

    /** A send failed but retries remain: back to PENDING, due again after the given delay. */
    public void markForRetry(Instant nextAttemptAt, String error) {
        this.attempts += 1;
        this.status = OutboxStatus.PENDING;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error;
    }

    /** Retries exhausted: parked for a human. */
    public void markDead(String error) {
        this.attempts += 1;
        this.status = OutboxStatus.DEAD;
        this.lastError = error;
    }

    /** The relay recovered a job stuck in flight after a crash; not counted as an attempt. */
    public void resetToPending() {
        this.status = OutboxStatus.PENDING;
    }
}
