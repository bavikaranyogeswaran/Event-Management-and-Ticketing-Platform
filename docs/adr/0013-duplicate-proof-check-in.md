# ADR-0013 — Duplicate-proof check-in

**Status:** Accepted · 2026-07-21 (decisions D34, D38, D39)

## Context
Check-in happens at a door, under time pressure, on venue wifi, often with two or three staff scanning at once. Repeat scans are not an edge case there — they are the normal texture of the job: a double tap, a retry after a request appears to hang, one attendee presented to two devices, a QR rescanned because the first beep was missed. Every one of those must be safe, because the cost of admitting a ticket twice is two people holding one seat, discovered only when the room is full. The design cannot rely on scans being rare or well spaced.

## Decision
**Two operations, only one of which changes anything.** `POST /check-ins/validate` is a pure read: staff can look at a ticket, see whose it is and whether it would be admitted, without consuming it. `POST /check-ins` is the single mutating call. Splitting them means the common "is this thing real?" question never risks burning a ticket.

**Admission is one transaction, and the database has the last word:** resolve the ticket → confirm it belongs to the event being scanned and is still `VALID` → insert the check-in row → mark the ticket `USED` → commit. The `unique (ticket_id)` constraint on `check_ins` is the arbiter. Two devices committing at the same instant do not race on application timing; one inserts, the other violates the constraint, and that violation is translated into the "already used" answer rather than an error.

**A duplicate scan is information, not a failure.** The response carries the original check-in time, so a staff member can tell "this person came in at 18:05" apart from "this ticket is fake" — two situations needing very different reactions at a door.

**Tickets are found by hashing the scanned token** and matching `ux_tickets_token_hash`; the raw token is never written to a log. The human-readable public code is the fallback for when a QR will not scan at all. The QR itself carries only the raw token (D34), which is derived from the ticket id and a server-side secret (D26) — unguessable without the secret, reproducible whenever a QR must be re-rendered, and never stored.

**Authority is resolved per event, never globally** (D38): staff reach only events assigned to them, organizers only events they own, admins any. Holding the STAFF role admits nobody by itself.

## Consequences
- ✅ A second admission is impossible even under simultaneous scans, because the outcome is decided by a unique index rather than by which request arrived first.
- ✅ Staff get an actionable answer on a repeat scan instead of a generic failure.
- ⚠️ Check-in needs connectivity (A-04). Offline scanning would require signed offline tokens and a reconciliation protocol, and is out of scope.
- ⚠️ Distinguishing "wrong event" from "unknown ticket" does tell an authorised scanner that a token is genuine but for another date. Accepted: only assigned staff can reach the endpoint, and collapsing the two would make the most common door mistake impossible to diagnose.
- ⚠️ There is no un-check-in. A mis-scan needs an administrator to correct the data, which is acceptable while the alternative is an endpoint that can free a ticket for re-entry.
