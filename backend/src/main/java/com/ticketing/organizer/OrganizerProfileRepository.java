package com.ticketing.organizer;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizerProfileRepository extends JpaRepository<OrganizerProfile, UUID> {

    Optional<OrganizerProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
