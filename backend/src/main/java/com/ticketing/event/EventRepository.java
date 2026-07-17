package com.ticketing.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {

    boolean existsBySlug(String slug);

    // owner-scoped lookup for organizer actions (excludes soft-deleted)
    Optional<Event> findByIdAndOrganizerIdAndDeletedAtIsNull(UUID id, UUID organizerId);

    List<Event> findByStatusAndEndsAtBefore(EventStatus status, Instant cutoff);
}
