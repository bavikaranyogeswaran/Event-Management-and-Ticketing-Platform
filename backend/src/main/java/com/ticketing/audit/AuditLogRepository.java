package com.ticketing.audit;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    long countByAction(String action);

    Optional<AuditLog> findFirstByActionOrderByCreatedAtDesc(String action);
}
