package com.ticketing.ticket;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    // owner-scoped fetch; a non-owner gets an empty result, never another user's ticket
    Optional<Ticket> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    List<Ticket> findByOrderIdOrderByIssuedAtAsc(UUID orderId);

    // check-in lookups: the scanned token is hashed first, the public code is the manual fallback
    Optional<Ticket> findByValidationTokenHash(String validationTokenHash);

    Optional<Ticket> findByPublicCode(String publicCode);

    // the holders to notify when an event is cancelled; read before the tickets are cancelled
    @Query("SELECT DISTINCT t.ownerUserId FROM Ticket t WHERE t.eventId = :eventId AND t.status = :status")
    List<UUID> findDistinctOwnerIdsByEventIdAndStatus(@Param("eventId") UUID eventId,
            @Param("status") TicketStatus status);

    // cancels the still-valid tickets; a used ticket stays used, since that attendance already happened
    @Modifying
    @Query("""
            UPDATE Ticket t SET t.status = com.ticketing.ticket.TicketStatus.CANCELLED, t.cancelledAt = :now
             WHERE t.eventId = :eventId AND t.status = com.ticketing.ticket.TicketStatus.VALID
            """)
    int cancelValidTicketsForEvent(@Param("eventId") UUID eventId, @Param("now") Instant now);

    // keyset first page of one user's tickets, newest first
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.ownerUserId = :ownerUserId
            ORDER BY t.issuedAt DESC, t.id DESC
            """)
    List<Ticket> findFirstOwnerTickets(@Param("ownerUserId") UUID ownerUserId, Limit limit);

    // keyset next page after a cursor position
    @Query("""
            SELECT t FROM Ticket t
            WHERE t.ownerUserId = :ownerUserId
              AND (t.issuedAt < :cursorTs OR (t.issuedAt = :cursorTs AND t.id < :cursorId))
            ORDER BY t.issuedAt DESC, t.id DESC
            """)
    List<Ticket> findOwnerTicketsAfter(@Param("ownerUserId") UUID ownerUserId,
            @Param("cursorTs") Instant cursorTs, @Param("cursorId") UUID cursorId, Limit limit);
}
