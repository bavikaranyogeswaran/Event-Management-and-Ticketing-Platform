package com.ticketing.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OutboxJobRepository extends JpaRepository<OutboxJob, UUID> {

    Optional<OutboxJob> findByJobKey(String jobKey);

    // locks the row so two deliveries of the same job cannot both send it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM OutboxJob j WHERE j.id = :id")
    Optional<OutboxJob> findByIdForUpdate(@Param("id") UUID id);

    // jobs the relay should publish now, oldest due first; matches ix_outbox_jobs_claim
    List<OutboxJob> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            OutboxStatus status, Instant cutoff, Limit limit);

    // jobs left in flight by a crash between claim and send, for the relay to recover
    List<OutboxJob> findByStatusAndUpdatedAtLessThan(OutboxStatus status, Instant cutoff);

    long countByStatus(OutboxStatus status);
}
