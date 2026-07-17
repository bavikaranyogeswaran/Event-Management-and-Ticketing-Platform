package com.ticketing.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.shared.port.IdGenerator;
import com.ticketing.shared.web.RequestIdFilter;

import tools.jackson.databind.ObjectMapper;

/** Writes audit entries. Joins the caller's transaction so a rolled-back action leaves no audit. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    AuditService(AuditLogRepository repository, IdGenerator idGenerator, Clock clock, ObjectMapper objectMapper) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String action, UUID actorUserId, Object detail) {
        record(action, actorUserId, null, null, detail);
    }

    @Transactional
    public void record(String action, UUID actorUserId, String entityType, UUID entityId, Object detail) {
        String detailJson = detail == null ? null : objectMapper.writeValueAsString(detail);
        repository.save(new AuditLog(idGenerator.newId(), actorUserId, action, entityType, entityId,
                detailJson, MDC.get(RequestIdFilter.MDC_KEY), Instant.now(clock)));
    }
}
