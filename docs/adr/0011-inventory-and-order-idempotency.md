# ADR-0011 — Zero-oversell inventory and idempotent order creation

**Status:** Accepted · 2026-07-18 (decisions D22, D24)

## Context
Order creation carries two hard invariants (requirements §3.3, risk R-06): confirmed tickets must never exceed configured quantity, and a retried request (network retry, double-click) must never create a duplicate order, duplicate tickets, or a double charge (FR-09). At the target load — up to 100 concurrent users on a single PostgreSQL instance — correctness must come from the database, not from application-level locks that a single JVM can hold but a restart or second instance cannot.

## Decision
**Inventory reservation is one atomic conditional update.** A sale bumps a single `quantity_sold` counter on `ticket_types`:
```sql
UPDATE ticket_types
   SET quantity_sold = quantity_sold + :qty
 WHERE id = :id AND status = 'ACTIVE'
   AND quantity_sold + :qty <= quantity_total;
```
Zero affected rows means the stock is gone → `409 TICKET_INVENTORY_EXHAUSTED`. One counter serves both reservation and sale; there is no separate reserved column. The table's `quantity_sold <= quantity_total` check constraint is the last-line guard behind the query.

**Free orders confirm in one ACID transaction:** validate → reserve (conditional update) → insert order + items + tickets → write the outbox confirmation row → commit. Any failure rolls the reservation back with the transaction — inventory is never leaked on a partial failure.

**Idempotency keys off the `Idempotency-Key` header.** The pair `(user_id, idempotency_key)` is unique (`ux_orders_idempotency`), and a `request_hash` — SHA-256 of the normalized request (event id + sorted items + attendees) — is stored on the order. On a key hit: same hash → return the original order (`200`); different hash → `409 IDEMPOTENCY_CONFLICT`. A missing header → `428 IDEMPOTENCY_KEY_REQUIRED`.

**Paid orders reuse the same counter** (Phase 8): reserve at creation, release by decrementing on expiry. Out of scope here — this phase is free-only (D23).

## Consequences
- ✅ Overselling is impossible at any concurrency; proven by a parallel-purchase integration test (the M1 exit test).
- ✅ Retries are safe and cheap — a unique constraint plus a hash compare, no distributed locks.
- ⚠️ One hot counter row can contend under extreme concurrency; acceptable at ≤ 100 concurrent users / < 500 attendees (A-05). The row lock is held only for the length of the update.
- ⚠️ `request_hash` depends on a stable request normalization; the canonicalization rule is defined once, with the idempotency helper, and covered by tests.
- ⚠️ For paid holds, reservation and expiry release must always net to zero — enforced by Phase 8 sweep tests.
