# ADR-0006 — Keyset pagination for user-facing lists

**Status:** Accepted · 2026-07-16

## Context
Offset pagination (`OFFSET n`) degrades linearly as offsets grow and produces duplicate/skipped rows when data changes between pages. Event lists, order history, ticket lists, and audit logs grow continuously and are user-facing.

## Decision
Keyset (cursor) pagination for all growing user-facing lists: stable sort on `(sortField, id)`, opaque validated cursor encoding the last row's sort values, response metadata `{limit, nextCursor, hasMore}`. Defaults 20 (attendee lists 50), max 100. Offset pagination is allowed only on small, bounded admin screens (`/admin/users`). Sort fields are allowlisted per endpoint.

## Consequences
- ✅ Constant-time page fetches backed by the composite indexes in the plan; no drift under concurrent inserts.
- ⚠️ No "jump to page N" — acceptable for feeds/history UIs.
- ⚠️ Cursors must be validated/signed server-side (never trusted raw), and each keyset endpoint needs its matching composite index.
