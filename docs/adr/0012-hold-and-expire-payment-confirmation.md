# ADR-0012 — Hold-and-expire inventory with idempotent payment confirmation

**Status:** Accepted · 2026-07-19 (decisions D28, D30, D31, D33)

## Context
A free order confirms inside one request (ADR-0011). A paid order cannot: the buyer leaves for a hosted checkout page and may pay in thirty seconds, pay in ten minutes, or never come back. Two problems follow. Seats must be held while they are away — but not forever, or an abandoned checkout quietly removes stock from a nearly sold-out event. And confirmation arrives later as a webhook, which providers deliver at least once: duplicates, delays, out-of-order retries, and forged requests are all normal traffic. Money is involved, so a double charge, a double ticket, or a silently swallowed payment are all unacceptable (risk R-07).

## Decision
**A pending order holds real stock.** Creation reserves through the same conditional update and single `quantity_sold` counter as a free order (ADR-0011); the order is written `PENDING_PAYMENT` with `expires_at` 15 minutes out. There is no separate "reserved" column and no second code path — a held seat and a sold seat are the same row arithmetic.

**Stock returns through one release statement**, the mirror of reserve:
```sql
UPDATE ticket_types SET quantity_sold = quantity_sold - :qty
 WHERE id = :id AND quantity_sold >= :qty;
```
Exactly two callers reach it: the scheduled expiry sweep and buyer cancellation. Both run it in the same transaction that moves the order to `EXPIRED`/`CANCELLED`, so status and stock can never disagree. The sweep is idempotent — an order already out of `PENDING_PAYMENT` fails its status guard and is skipped.

**Tickets exist only after payment is confirmed.** A pending order holds inventory and has no tickets, so an unpaid order can never produce something scannable.

**Confirmation is one transaction:** verify the signature → lock the order row → insert the payment behind `unique (provider, provider_payment_id)` → verify amount and currency against the stored order → confirm the order → issue tickets → write the outbox row → commit. A duplicate event loses the unique-constraint race and is answered `200` with no side effects, because a provider that gets an error will retry a delivery that was in fact already handled.

**Nothing fakes success.** A failed or abandoned payment leaves the order `PENDING_PAYMENT`, retryable until it expires.

**Payment and expiry can arrive together**, and that race is the sharp edge: money taken while the seats were just released. The order row lock serialises the two, so one observes the other's result. If confirmation loses, it retries the conditional update — seats still free means the order confirms normally; genuinely sold out means the payment is still recorded, the order stays `EXPIRED`, and the case is audit-logged for the manual refund path (D4).

## Consequences
- ✅ Overselling stays impossible: every path in and out of stock is the same guarded statement, so held, sold, released, and re-claimed seats all obey one invariant.
- ✅ Webhooks are safe to replay, which is what providers actually do; duplicate delivery is a no-op rather than a second charge or a second ticket.
- ✅ Abandoned checkouts self-heal within the window, and a buyer who cancels frees seats immediately.
- ⚠️ Money can be taken for an order that cannot be honoured. It is recorded and flagged rather than hidden, but resolving it is a manual, out-of-band refund — acceptable only because D4 already makes refunds manual.
- ⚠️ The sweep is in-process (D28). A stopped application stops expiring orders; stock returns late once it restarts, never incorrectly.
- ⚠️ A 15-minute hold is a guess. Too short strands slow payers, too long starves a busy event. It is configurable and should be revisited against real abandonment data.
