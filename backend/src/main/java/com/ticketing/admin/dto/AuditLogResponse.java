package com.ticketing.admin.dto;

import java.time.Instant;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

public record AuditLogResponse(
        UUID logId,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        JsonNode detail,
        String requestId,
        Instant createdAt) {
}
