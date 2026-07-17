package com.ticketing.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID> {

    boolean existsBySlug(String slug);

    // owner-scoped lookup for organizer actions (excludes soft-deleted)
    Optional<Event> findByIdAndOrganizerIdAndDeletedAtIsNull(UUID id, UUID organizerId);

    List<Event> findByStatusAndEndsAtBefore(EventStatus status, Instant cutoff);

    // keyset first page of one organizer's events, newest first
    @Query("""
            SELECT e FROM Event e
            WHERE e.organizerId = :organizerId AND e.deletedAt IS NULL
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<Event> findFirstOrganizerEvents(@Param("organizerId") UUID organizerId, Limit limit);

    // keyset next page after a cursor position
    @Query("""
            SELECT e FROM Event e
            WHERE e.organizerId = :organizerId AND e.deletedAt IS NULL
              AND (e.createdAt < :cursorTs OR (e.createdAt = :cursorTs AND e.id < :cursorId))
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<Event> findOrganizerEventsAfter(@Param("organizerId") UUID organizerId,
            @Param("cursorTs") Instant cursorTs, @Param("cursorId") UUID cursorId, Limit limit);

    // ---- admin listing across all organizers (excludes soft-deleted) ----

    @Query("SELECT e FROM Event e WHERE e.deletedAt IS NULL ORDER BY e.createdAt DESC, e.id DESC")
    List<Event> findAdminEvents(Limit limit);

    @Query("""
            SELECT e FROM Event e
            WHERE e.deletedAt IS NULL
              AND (e.createdAt < :cursorTs OR (e.createdAt = :cursorTs AND e.id < :cursorId))
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<Event> findAdminEventsAfter(@Param("cursorTs") Instant cursorTs, @Param("cursorId") UUID cursorId,
            Limit limit);

    @Query("""
            SELECT e FROM Event e WHERE e.deletedAt IS NULL AND e.status = :status
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<Event> findAdminEventsByStatus(@Param("status") EventStatus status, Limit limit);

    @Query("""
            SELECT e FROM Event e
            WHERE e.deletedAt IS NULL AND e.status = :status
              AND (e.createdAt < :cursorTs OR (e.createdAt = :cursorTs AND e.id < :cursorId))
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<Event> findAdminEventsByStatusAfter(@Param("status") EventStatus status,
            @Param("cursorTs") Instant cursorTs, @Param("cursorId") UUID cursorId, Limit limit);
}
