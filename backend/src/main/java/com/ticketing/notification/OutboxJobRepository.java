package com.ticketing.notification;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxJobRepository extends JpaRepository<OutboxJob, UUID> {

    Optional<OutboxJob> findByJobKey(String jobKey);
}
