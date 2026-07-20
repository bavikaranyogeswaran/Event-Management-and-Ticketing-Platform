# Assumptions, Decisions & Risk Register

**Version:** 1.0-draft · **Date:** 2026-07-16 · **Status:** Pending sign-off (Phase 1, Step 1.8)
**Related docs:** [requirements.md](requirements.md) · [mvp-scope.md](mvp-scope.md)

---

## 1. Decision Register (resolved 2026-07-16)

| # | Question | Decision | Rationale / consequence |
|---|----------|----------|--------------------------|
| D1 | Country & currency | **Sri Lanka / LKR** | Single country, single currency; prices stored and displayed in LKR. |
| D2 | Organizer publishing | **Admin approval required** | Adds `PENDING_REVIEW`/`REJECTED` event states, admin review queue, decision notifications (FR-21). |
| D3 | Guest checkout | **No — login required** | Clean ticket ownership, order history, and check-in authorization. |
| D4 | Refunds | **No automated refunds** | Cancellations happen in-app; money movement handled off-platform manually. No refund states in MVP. |
| D5 | Payment provider | **Stripe test mode** | Best sandbox/dev experience. Dev-only — see risk R-01. |
| D6 | Email provider | **Gmail SMTP** (app password) | Free, instant setup, ~500 emails/day. Swappable via `EmailSender` port. |
| D7 | Redis & RabbitMQ hosting | **Native Windows installs** | Memurai (Redis-compatible, free dev edition) + RabbitMQ Windows installer (Erlang). Local, offline-capable, no Docker. |
| D8 | UI library | **Material UI (MUI)** | Fastest path for dashboard/table-heavy organizer & admin screens. |
| D9 | Should-have features | **All four groups in MVP** | Password reset + email verification · Cloudinary image upload · CSV export + ticket PDF · rate limiting + sales reporting. |
| D10 | Could-have features | **Reminder emails in; rest deferred** | Waitlists, promo codes, calendar export, event cloning → post-MVP. |
| D11 | NFR targets | **Accepted as documented** | p95 budgets, 100 concurrent users, ~99% availability, zero oversell/duplicates, RPO 24 h / RTO 8 h. |
| D12 | Testing depth | **Core only** | Unit + integration (Testcontainers) + API tests. No Playwright E2E, no k6. Concurrency integration tests retained to prove correctness invariants. |
| D13 | Password hashing (2026-07-17) | **BCrypt** | Spring Security default via DelegatingPasswordEncoder — stored format survives a future algorithm swap. |
| D14 | Email verification (2026-07-17) | **Verified to transact** | Register + log in freely; buying tickets and creating events require a verified email. Enforced in application services. |
| D15 | Organizer role (2026-07-17) | **Self-service upgrade** | Creating an organizer profile (Phase 6) grants ORGANIZER; requires verified email. Quality gate stays at admin event review (ADR-0007). |
| D16 | Event slugs (2026-07-17) | **Auto from title + suffix, fixed at creation** | Server derives slug from title, appends a short suffix on collision. Never changes after creation, even if the title is later edited. |
| D17 | Editing a PUBLISHED event (2026-07-17) | **Descriptive fields only** | Title, description, banner editable when published; dates/venue/capacity/type locked once published (would mislead ticket holders). All edits audit-logged. Refines A-13. |
| D18 | Ticket-type edits after a sale (2026-07-17) | **Quantity up only, price locked** | Before any sale: fully editable. After first sale: price locked, total quantity can only increase (never below quantity_sold); name/description still editable. |
| D19 | Public event search (2026-07-17) | **Title only, ILIKE** | Case-insensitive substring match on title. Full-text/description search deferred until measured need. |
| D20 | Admin approval endpoint (2026-07-17) | **Single review endpoint** | One `POST /admin/events/{id}/review` with `{decision, reason?}`; reason required on rejection. Matches the API contract. |
| D21 | Ticket read endpoints (2026-07-18) | **JSON reads with the order phase** | `GET /users/me/tickets` and `GET /tickets/{id}` return JSON alongside order creation; the QR-PNG image and PDF download are deferred to the ticket-media phase. Keeps the free-order slice viewable end-to-end. |
| D22 | Idempotency conflict detection (2026-07-18) | **`request_hash` on the order** | Store a SHA-256 of the normalized order request; same `(user, key)` + same hash → replay the original order (200), same key + different hash → 409 `IDEMPOTENCY_CONFLICT`, missing header → 428. Backed by `ux_orders_idempotency (user_id, idempotency_key)`. |
| D23 | Paid orders (2026-07-18, reopened 2026-07-19) | **Free settles instantly, priced orders hold** | A zero-total order is `CONFIRMED` in one transaction with its tickets. A priced order claims the same seats but is written `PENDING_PAYMENT` with a deadline and issues no tickets until payment confirms (D30). Attendee names are kept on the order line (V9) so those tickets can still be issued later. The temporary `PAYMENTS_NOT_ENABLED` rejection is gone. |
| D24 | Inventory reservation model (2026-07-18) | **Single counter + conditional update** | One `quantity_sold` column serves both reservation and sale; an atomic `UPDATE … WHERE quantity_sold + :n <= quantity_total` reserves, zero rows → `TICKET_INVENTORY_EXHAUSTED`. No separate reserved column. See ADR-0011. |
| D25 | Order number & ticket identifiers (2026-07-18) | **Readable order no.; hashed ticket token** | Order number `ORD-<year>-<seq>` (config-driven prefix). Ticket public code is random and human-enterable (unique). Only the validation token's SHA-256 hash is stored (`ux_tickets_token_hash`); the raw token lives only inside the QR and is never logged. Token derivation refined by D26. |
| D26 | Ticket validation token (2026-07-18) | **Derived, not random** | The token is `HMAC-SHA256(app.ticket.token-secret, ticketId)` instead of a random value, so the server can recompute it whenever a QR must be re-rendered — a random token would be unrecoverable once only its hash is stored, leaving `GET /tickets/{id}/qr` with nothing to encode. The secret lives in `.env`, never in the database, so a database dump alone still cannot forge a ticket. Amends FR-12; rotating the secret invalidates every existing QR. |
| D27 | Payment provider integration (2026-07-19) | **Stripe test mode behind a port** | Real Stripe SDK, real Checkout Sessions, real signed webhooks — but only ever reached through the `PaymentGateway` port, so the provider swap that R-01 forces before production touches one adapter. |
| D28 | Pending-order expiry (2026-07-19) | **In-process scheduled sweep, 15-minute hold** | A fixed-delay `@Scheduled` job claims due orders in batches and returns their stock. No extra infrastructure, and the sweep can be invoked directly in tests. Matches the single-instance deployment this project targets. |
| D29 | Abandoned checkouts (2026-07-19) | **Buyer can cancel a pending order** | `POST /orders/{id}/cancel` frees the seats immediately rather than holding them for the full window. Reuses the same release path the sweep needs, so it costs an endpoint and its tests. |
| D30 | When paid tickets exist (2026-07-19) | **Only after payment is confirmed** | A `PENDING_PAYMENT` order holds inventory but has no tickets. Tickets are created inside the webhook confirmation transaction, so an unpaid order can never yield a scannable ticket. |
| D31 | Duplicate webhook events (2026-07-19) | **Deduplicate, then acknowledge** | `unique (provider, provider_payment_id)` decides the winner; a duplicate event is answered `200` with no side effects. Returning an error would make the provider retry forever a delivery that was already handled. |
| D32 | Amounts sent to the gateway (2026-07-19) | **Integer minor units; gateway currency configurable** | Money crosses the port as integer cents to avoid float drift. The domain stays LKR (D1); `app.payment.gateway-currency` exists because a Stripe test account may not accept LKR, and test mode must not dictate the domain. |
| D33 | Payment landing after expiry (2026-07-19) | **Try to re-claim stock, else flag for refund** | The conditional update is attempted again: if seats remain the order confirms normally; if not, the payment is still recorded, the order stays `EXPIRED`, and the case is audit-logged for the manual refund path (D4). Money received is never silently discarded, and stock is never oversold to honour a late payment. |
| D34 | What the QR carries (2026-07-21) | **The raw validation token only** | No URL, so the token never lands in browser history or an intermediate access log. Staff scan through the check-in screen, which posts the scanned value with the event it is being scanned for. |
| D35 | Ticket PDF rendering (2026-07-21) | **OpenPDF** | LGPL/MPL fork of iText 4, drawn directly in Java — enough for a one-page ticket without pulling in a second rendering engine. iText 7 is AGPL and would be a licensing problem for this project. |
| D36 | Adding event staff (2026-07-21) | **By email, account must exist** | The organizer enters an email that is already registered; the assignment also grants the STAFF role. No invitation lifecycle, no pending state, and the person keeps control of their own account. |
| D37 | Removing event staff (2026-07-21) | **STAFF role goes with the last assignment** | Unassigning someone from their final event revokes STAFF, so nobody keeps a role that authorises nothing. Assignments elsewhere keep it. |
| D38 | Who may check tickets in (2026-07-21) | **Resolved per event, never globally** | Staff reach only events they are assigned to, organizers only events they own, admins any. A STAFF role by itself admits nobody. |
| D39 | Telling scans apart (2026-07-21) | **Distinct reasons for authorised staff** | A ticket for another event answers differently from an unknown token, which does reveal that a token is real. Accepted: only assigned staff reach the endpoint, and merging the two would make the most common door mistake undiagnosable. |
| D40 | Cancelling an event with tickets (2026-07-21) | **Its tickets are cancelled too** | Event cancellation marks every issued ticket CANCELLED and queues a notice to each holder, so a cancelled event cannot be checked in to. Completes FR-06, which could not be finished before tickets existed. |

---

## 2. Assumption Register

| # | Assumption | Impact if wrong |
|---|------------|-----------------|
| A-01 | One country (LK), one currency (LKR), one language (English) | Multi-currency/i18n would touch pricing, orders, emails, and all UI text. |
| A-02 | General-admission tickets only — no reserved seating or seat maps | Seat maps are a major data-model and UI change (explicitly postponed). |
| A-03 | Hosted payment provider; no card data ever stored internally | Storing card data would trigger PCI-DSS scope — never planned. |
| A-04 | Online-only ticket validation (staff device has connectivity) | Offline scanning would require signed offline tokens + sync protocol (postponed). |
| A-05 | Events normally < 500 attendees; ~5,000 tickets platform-wide | Larger scale revisits caching, inventory contention, and read replicas (master plan Phase 20 triggers). |
| A-06 | Single managed/local PostgreSQL instance is sufficient | Sharding/replicas explicitly rejected until measured need. |
| A-07 | Browser SPA only — no native mobile apps, no SMS/WhatsApp | New client types would justify token-based auth review (sessions chosen for browser-only). |
| A-08 | Ticket transfers between users are excluded | Transfer flows touch ownership, validation, and fraud controls (postponed). |
| A-09 | No platform service fee; order total = sum of ticket prices | Fee logic would change order calculation, reporting, and Stripe amounts. |
| A-10 | Taxes and invoicing are the organizer's responsibility; platform issues no invoices | Tax engines/invoice generation are out of scope for MVP. |
| A-11 | Data retention: indefinite for MVP; manual admin deletion; soft delete for accounts | A retention policy must be defined before real production use (local privacy law). |
| A-12 | Buyer may enter a distinct attendee name per ticket (`attendees[]` in order API) | Matches the plan's order contract; removing it simplifies orders slightly. |
| A-13 | Editing critical fields of a PUBLISHED event does not trigger re-review (changes audit-logged) | If admin re-review is required on edits, FR-21 gains a re-review state machine. |
| A-14 | One developer builds and operates everything | Largest schedule risk per the source plan; scope discipline is the mitigation. |
| A-15 | Dev machine is Windows 11 without Docker; all services run natively | Team growth or deployment would revisit environment reproducibility (out of scope now). |

---

## 3. Risk Register

| # | Risk | Severity | Mitigation |
|---|------|----------|------------|
| R-01 | **Stripe has no live merchant support in Sri Lanka** — test mode is dev-only; live LKR payments impossible on Stripe | High (blocks real payments, not MVP) | `PaymentGateway` port isolates the provider; swap to PayHere (or similar) before production. No Stripe-specific logic outside the adapter + webhook parsing. |
| R-02 | Gmail SMTP limits (~500/day) and deliverability (SPF/DKIM of gmail.com) | Medium | Volume is far below limits for MVP; `EmailSender` port allows swap to Resend/SendGrid with a domain later. |
| R-03 | Memurai is Redis-*compatible*, not Redis — edge-case behavior may differ | Low | Only standard commands used (sessions, cache, rate-limit buckets); integration tests run against real Redis via Testcontainers. |
| R-04 | RabbitMQ on Windows (Erlang dependency, service quirks) | Low–Medium | Well-documented installer path; management UI for inspection; outbox pattern tolerates broker downtime (jobs wait in PostgreSQL). |
| R-05 | Admin approval workflow (D2) adds scope vs. original plan | Medium | Contained: one extra state pair, one admin queue screen, two email templates. Frozen — no further workflow additions in MVP. |
| R-06 | Overselling / duplicate tickets / duplicate check-ins under concurrency | Critical | DB-level defenses (conditional update, unique constraints) + retained concurrency integration tests (D12 exception). |
| R-07 | Duplicate payment processing via webhook replay | Critical | Signature verification, `unique (provider, provider_payment_id)`, amount/currency match, idempotent confirmation transaction. |
| R-08 | JPA N+1 queries and oversized entity graphs degrade p95 targets | Medium | Explicit queries/projections for hot paths; slow-query review before release (Phase 19). |
| R-09 | No E2E/load tests (D12) weakens release confidence | Medium | Accepted trade-off; API + concurrency integration tests cover critical flows; manual browser passes before release. |
| R-10 | Scope growth (solo developer) | High | This document freezes scope; anything new goes to the post-MVP backlog in [mvp-scope.md](mvp-scope.md). |
| R-11 | Cloudinary free-tier limits (storage/bandwidth/transformations) | Low | MVP volumes are tiny; usage visible in Cloudinary dashboard; `ObjectStorage` port allows provider swap. |

---

## 4. Defaults in force (unless explicitly changed)

- Order expiration for pending payments: **15 minutes**.
- Reminder email: **24 hours before event start**, once per ticket holder per event.
- Session idle timeout: **30 days remember-me OFF; standard timeout 24 h** (tunable at Phase 5).
- Pagination: default 20 (attendee lists 50), max 100, stable `field,id` sort, keyset for user-facing lists, offset for small admin screens.
- Token TTLs: password reset **60 min**, email verification **24 h** — single-use, hash-stored.
- Upload limits: banner ≤ 5 MB; profile/thumbnail ≤ 2 MB; JPEG/PNG/WebP only.
- Email retry backoff: 1 m → 5 m → 30 m → 2 h → dead-letter.
- Backups: daily logical dump; retain 7 daily + 4 weekly; restore drill once before release.
