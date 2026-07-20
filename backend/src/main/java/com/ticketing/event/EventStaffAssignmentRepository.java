package com.ticketing.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventStaffAssignmentRepository extends JpaRepository<EventStaffAssignment, UUID> {

    List<EventStaffAssignment> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    Optional<EventStaffAssignment> findByEventIdAndUserId(UUID eventId, UUID userId);

    // the question check-in asks: may this person work this door?
    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);

    // decides whether removing one assignment leaves the STAFF role with nothing to authorise
    long countByUserId(UUID userId);
}
