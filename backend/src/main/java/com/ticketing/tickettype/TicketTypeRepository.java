package com.ticketing.tickettype;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    List<TicketType> findByEventIdAndStatus(UUID eventId, TicketTypeStatus status);

    long countByEventIdAndStatus(UUID eventId, TicketTypeStatus status);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);
}
