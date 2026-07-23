package com.ticketing.reporting;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.event.EventService;
import com.ticketing.reporting.dto.AttendeeResponse;
import com.ticketing.reporting.dto.EventStatsResponse;
import com.ticketing.reporting.dto.OrganizerOrderSummary;
import com.ticketing.shared.pagination.KeysetCursor;

@Service
public class ReportingService {

    // Subqueries hang off the event row so the query always returns exactly one row.
    private static final String STATS_SQL = """
            SELECT
              coalesce((SELECT sum(tt.quantity_sold)
                        FROM ticket_types tt WHERE tt.event_id = e.id), 0)                      AS tickets_sold,
              coalesce((SELECT sum(greatest(tt.quantity_total - tt.quantity_sold, 0))
                        FROM ticket_types tt WHERE tt.event_id = e.id), 0)                      AS remaining_capacity,
              coalesce((SELECT sum(o.grand_total)
                        FROM orders o WHERE o.event_id = e.id AND o.status = 'CONFIRMED'), 0)   AS revenue,
              (SELECT count(*) FROM check_ins ci WHERE ci.event_id = e.id)                      AS check_in_count,
              (SELECT count(*) FROM orders o
               WHERE o.event_id = e.id AND o.status = 'CONFIRMED')                             AS confirmed_orders,
              (SELECT count(*) FROM orders o
               WHERE o.event_id = e.id AND o.status IN ('CANCELLED', 'EXPIRED'))               AS cancelled_orders
            FROM events e
            WHERE e.id = ?
            """;

    private static final String ORDERS_SQL = """
            SELECT o.id, o.order_number, o.status, o.grand_total, o.currency,
                   o.created_at, count(t.id) AS ticket_count
            FROM orders o
            LEFT JOIN tickets t ON t.order_id = o.id
            WHERE o.event_id = ?
            GROUP BY o.id, o.order_number, o.status, o.grand_total, o.currency, o.created_at
            ORDER BY o.created_at DESC, o.id DESC
            LIMIT ?
            """;

    private static final String ORDERS_AFTER_SQL = """
            SELECT o.id, o.order_number, o.status, o.grand_total, o.currency,
                   o.created_at, count(t.id) AS ticket_count
            FROM orders o
            LEFT JOIN tickets t ON t.order_id = o.id
            WHERE o.event_id = ?
              AND (o.created_at < ? OR (o.created_at = ? AND o.id < ?))
            GROUP BY o.id, o.order_number, o.status, o.grand_total, o.currency, o.created_at
            ORDER BY o.created_at DESC, o.id DESC
            LIMIT ?
            """;

    private static final String ATTENDEES_SQL = """
            SELECT t.id, t.public_code, t.attendee_name, tt.name AS ticket_type_name,
                   t.status, t.issued_at, ci.checked_in_at
            FROM tickets t
            JOIN ticket_types tt ON tt.id = t.ticket_type_id
            LEFT JOIN check_ins ci ON ci.ticket_id = t.id
            WHERE t.event_id = ?
            ORDER BY t.issued_at DESC, t.id DESC
            LIMIT ?
            """;

    private static final String ATTENDEES_AFTER_SQL = """
            SELECT t.id, t.public_code, t.attendee_name, tt.name AS ticket_type_name,
                   t.status, t.issued_at, ci.checked_in_at
            FROM tickets t
            JOIN ticket_types tt ON tt.id = t.ticket_type_id
            LEFT JOIN check_ins ci ON ci.ticket_id = t.id
            WHERE t.event_id = ?
              AND (t.issued_at < ? OR (t.issued_at = ? AND t.id < ?))
            ORDER BY t.issued_at DESC, t.id DESC
            LIMIT ?
            """;

    private static final RowMapper<OrganizerOrderSummary> ORDER_MAPPER = (rs, rn) ->
            new OrganizerOrderSummary(
                    rs.getObject("id", UUID.class),
                    rs.getString("order_number"),
                    rs.getString("status"),
                    rs.getBigDecimal("grand_total"),
                    rs.getString("currency").strip(),
                    (int) rs.getLong("ticket_count"),
                    rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<AttendeeResponse> ATTENDEE_MAPPER = (rs, rn) -> {
        Timestamp checkedIn = rs.getTimestamp("checked_in_at");
        return new AttendeeResponse(
                rs.getObject("id", UUID.class),
                rs.getString("public_code"),
                rs.getString("attendee_name"),
                rs.getString("ticket_type_name"),
                rs.getString("status"),
                rs.getTimestamp("issued_at").toInstant(),
                checkedIn != null ? checkedIn.toInstant() : null);
    };

    private final EventService eventService;
    private final JdbcTemplate jdbc;

    ReportingService(EventService eventService, JdbcTemplate jdbc) {
        this.eventService = eventService;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public EventStatsResponse getEventStats(UUID eventId, UUID organizerId) {
        eventService.getOwnedEvent(eventId, organizerId); // 404 if not owned
        return jdbc.queryForObject(STATS_SQL,
                (rs, rn) -> new EventStatsResponse(
                        rs.getInt("tickets_sold"),
                        rs.getInt("remaining_capacity"),
                        rs.getBigDecimal("revenue"),
                        "LKR",
                        rs.getLong("check_in_count"),
                        rs.getLong("confirmed_orders"),
                        rs.getLong("cancelled_orders")),
                eventId);
    }

    /** Returns up to {@code pageSize + 1} rows; the caller uses the extra row to detect more pages. */
    @Transactional(readOnly = true)
    public List<OrganizerOrderSummary> listEventOrders(UUID eventId, UUID organizerId,
            KeysetCursor.Position cursor, int pageSize) {
        eventService.getOwnedEvent(eventId, organizerId);
        if (cursor == null) {
            return jdbc.query(ORDERS_SQL, ORDER_MAPPER, eventId, pageSize + 1);
        }
        Timestamp ts = Timestamp.from(cursor.timestamp());
        return jdbc.query(ORDERS_AFTER_SQL, ORDER_MAPPER, eventId, ts, ts, cursor.id(), pageSize + 1);
    }

    /** Returns up to {@code pageSize + 1} rows; the caller uses the extra row to detect more pages. */
    @Transactional(readOnly = true)
    public List<AttendeeResponse> listEventAttendees(UUID eventId, UUID organizerId,
            KeysetCursor.Position cursor, int pageSize) {
        eventService.getOwnedEvent(eventId, organizerId);
        if (cursor == null) {
            return jdbc.query(ATTENDEES_SQL, ATTENDEE_MAPPER, eventId, pageSize + 1);
        }
        Timestamp ts = Timestamp.from(cursor.timestamp());
        return jdbc.query(ATTENDEES_AFTER_SQL, ATTENDEE_MAPPER, eventId, ts, ts, cursor.id(), pageSize + 1);
    }
}
