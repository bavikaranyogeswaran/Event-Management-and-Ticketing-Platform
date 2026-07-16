# ADR-0007 — Admin approval required before event publication

**Status:** Accepted · 2026-07-16 (decision D2)

## Context
The source plan left organizer self-publish vs. admin approval as an open question. The user chose mandatory admin review: the platform curates quality and blocks fraudulent or misconfigured events before they become publicly visible and sellable.

## Decision
Event lifecycle gains two states: `DRAFT → PENDING_REVIEW → PUBLISHED | REJECTED`, with `REJECTED → DRAFT` for rework and resubmission. Submission validates publication rules (coherent dates, venue for physical events, capacity > 0, ≥ 1 valid ticket type). Admins act through a review queue (`GET /admin/events?status=PENDING_REVIEW`, `POST /admin/events/{id}/review`); rejection requires a reason. Decisions are audit-logged and emailed to the organizer. Editing is blocked while PENDING_REVIEW (withdraw to DRAFT first). MVP rule (assumption A-13): edits to a PUBLISHED event do not trigger re-review; they are audit-logged.

## Consequences
- ✅ Platform-level quality/fraud gate before anything is sellable.
- ⚠️ Publication latency now depends on admin responsiveness; an unattended queue blocks organizers (mitigation: queue visible on admin landing screen).
- ⚠️ Adds one queue screen, one review endpoint, two email templates, two states — deliberately frozen at this size (risk R-05).
