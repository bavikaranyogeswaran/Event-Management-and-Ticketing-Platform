package com.ticketing.checkin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    // a duplicate scan reads this back to report when the attendee first came in
    Optional<CheckIn> findByTicketId(UUID ticketId);

    long countByEventId(UUID eventId);
}
