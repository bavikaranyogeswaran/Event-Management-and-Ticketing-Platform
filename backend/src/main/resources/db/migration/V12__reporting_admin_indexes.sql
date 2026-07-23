-- Indexes added for Phase 12: organizer dashboard keyset queries and admin user list.
-- All three fill gaps that the Phase 1-4 indexes cannot cover without a leading status column.

-- Organizer orders list: keyset across all statuses for one event.
-- ix_orders_event_status_created leads with status, so it cannot serve this scan without a status filter.
CREATE INDEX ix_orders_event_created ON orders (event_id, created_at DESC, id);

-- Organizer attendees list: keyset by issued_at across all ticket statuses for one event.
-- ix_tickets_event_status leads with (event_id, status) and has no issued_at for cursor comparisons.
CREATE INDEX ix_tickets_event_issued ON tickets (event_id, issued_at DESC, id);

-- Admin user list: keyset over all users by creation time without a leading status filter.
-- ix_users_status_created has status as the leading column and cannot cover this scan.
CREATE INDEX ix_users_created ON users (created_at DESC, id);
