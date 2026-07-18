package com.ticketing.tickettype;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    List<TicketType> findByEventIdAndStatus(UUID eventId, TicketTypeStatus status);

    long countByEventIdAndStatus(UUID eventId, TicketTypeStatus status);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);

    /**
     * Claims stock in one atomic statement — the guard against overselling.
     * Returns 0 when the type is inactive or the quantity would exceed what is left;
     * callers must treat 0 as a failure and never fall back to a read-then-write.
     * The in-memory entity is stale afterwards; re-read if the new count is needed.
     */
    @Modifying
    @Query("""
            UPDATE TicketType t
               SET t.quantitySold = t.quantitySold + :quantity
             WHERE t.id = :id
               AND t.status = com.ticketing.tickettype.TicketTypeStatus.ACTIVE
               AND t.quantitySold + :quantity <= t.quantityTotal
            """)
    int reserve(@Param("id") UUID id, @Param("quantity") int quantity);
}
