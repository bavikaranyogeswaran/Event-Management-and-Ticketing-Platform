package com.ticketing.admin;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.admin.dto.AuditLogResponse;
import com.ticketing.shared.pagination.KeysetCursor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class AdminAuditLogService {

    private static final String LOGS_SQL = """
            SELECT al.id, al.actor_user_id, al.action, al.entity_type, al.entity_id,
                   al.detail, al.request_id, al.created_at
            FROM audit_logs al
            ORDER BY al.created_at DESC, al.id DESC
            LIMIT ?
            """;

    private static final String LOGS_AFTER_SQL = """
            SELECT al.id, al.actor_user_id, al.action, al.entity_type, al.entity_id,
                   al.detail, al.request_id, al.created_at
            FROM audit_logs al
            WHERE (al.created_at < ? OR (al.created_at = ? AND al.id < ?))
            ORDER BY al.created_at DESC, al.id DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final RowMapper<AuditLogResponse> logMapper;

    AdminAuditLogService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.logMapper = (rs, rn) -> {
            String detailJson = rs.getString("detail");
            JsonNode detail = null;
            if (detailJson != null) {
                detail = objectMapper.readTree(detailJson);
            }
            return new AuditLogResponse(
                    rs.getObject("id", UUID.class),
                    rs.getObject("actor_user_id", UUID.class),
                    rs.getString("action"),
                    rs.getString("entity_type"),
                    rs.getObject("entity_id", UUID.class),
                    detail,
                    rs.getString("request_id"),
                    rs.getTimestamp("created_at").toInstant());
        };
    }

    /** Returns up to {@code pageSize + 1} rows; caller wraps in PageResponse to detect more pages. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> listLogs(KeysetCursor.Position cursor, int pageSize) {
        if (cursor == null) {
            return jdbc.query(LOGS_SQL, logMapper, pageSize + 1);
        }
        Timestamp ts = Timestamp.from(cursor.timestamp());
        return jdbc.query(LOGS_AFTER_SQL, logMapper, ts, ts, cursor.id(), pageSize + 1);
    }
}
