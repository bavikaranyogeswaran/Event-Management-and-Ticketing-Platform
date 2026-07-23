package com.ticketing.admin;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.admin.dto.UserSummaryResponse;
import com.ticketing.shared.pagination.KeysetCursor;

@Service
class AdminUserService {

    private static final String USERS_SQL = """
            SELECT u.id, u.email, u.display_name, u.status, u.email_verified_at, u.created_at
            FROM users u
            WHERE u.deleted_at IS NULL
            ORDER BY u.created_at DESC, u.id DESC
            LIMIT ?
            """;

    private static final String USERS_AFTER_SQL = """
            SELECT u.id, u.email, u.display_name, u.status, u.email_verified_at, u.created_at
            FROM users u
            WHERE u.deleted_at IS NULL
              AND (u.created_at < ? OR (u.created_at = ? AND u.id < ?))
            ORDER BY u.created_at DESC, u.id DESC
            LIMIT ?
            """;

    private static final RowMapper<UserSummaryResponse> USER_MAPPER = (rs, rn) ->
            new UserSummaryResponse(
                    rs.getObject("id", java.util.UUID.class),
                    rs.getString("email"),
                    rs.getString("display_name"),
                    rs.getString("status"),
                    rs.getTimestamp("email_verified_at") != null,
                    rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;

    AdminUserService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns up to {@code pageSize + 1} rows; caller wraps in PageResponse to detect more pages. */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers(KeysetCursor.Position cursor, int pageSize) {
        if (cursor == null) {
            return jdbc.query(USERS_SQL, USER_MAPPER, pageSize + 1);
        }
        Timestamp ts = Timestamp.from(cursor.timestamp());
        return jdbc.query(USERS_AFTER_SQL, USER_MAPPER, ts, ts, cursor.id(), pageSize + 1);
    }
}
