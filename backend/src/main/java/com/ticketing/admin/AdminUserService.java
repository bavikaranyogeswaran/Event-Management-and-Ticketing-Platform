package com.ticketing.admin;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.admin.dto.UserSummaryResponse;
import com.ticketing.audit.AuditActions;
import com.ticketing.audit.AuditService;
import com.ticketing.shared.api.ApiException;
import com.ticketing.shared.api.ResourceNotFoundException;
import com.ticketing.shared.pagination.KeysetCursor;
import com.ticketing.shared.session.UserSessionService;
import com.ticketing.user.User;
import com.ticketing.user.UserRepository;
import com.ticketing.user.UserStatus;

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
                    rs.getObject("id", UUID.class),
                    rs.getString("email"),
                    rs.getString("display_name"),
                    rs.getString("status"),
                    rs.getTimestamp("email_verified_at") != null,
                    rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final UserSessionService userSessionService;
    private final AuditService auditService;

    AdminUserService(JdbcTemplate jdbc, UserRepository userRepository,
            UserSessionService userSessionService, AuditService auditService) {
        this.jdbc = jdbc;
        this.userRepository = userRepository;
        this.userSessionService = userSessionService;
        this.auditService = auditService;
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

    @Transactional
    public UserSummaryResponse updateStatus(UUID targetUserId, UserStatus newStatus, UUID adminUserId) {
        if (newStatus == UserStatus.DELETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS_TRANSITION",
                    "Status DELETED may not be set via this endpoint.");
        }
        User user = userRepository.findById(targetUserId)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(ResourceNotFoundException::new);

        if (user.getStatus() == newStatus) {
            return toSummary(user);
        }

        user.setStatus(newStatus);

        if (newStatus == UserStatus.SUSPENDED) {
            userSessionService.invalidateAll(user.getEmail());
            auditService.record(AuditActions.USER_SUSPENDED, adminUserId, "USER", targetUserId, null);
        } else {
            auditService.record(AuditActions.USER_REACTIVATED, adminUserId, "USER", targetUserId, null);
        }

        return toSummary(user);
    }

    private static UserSummaryResponse toSummary(User user) {
        return new UserSummaryResponse(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getStatus().name(), user.isEmailVerified(), user.getCreatedAt());
    }
}
