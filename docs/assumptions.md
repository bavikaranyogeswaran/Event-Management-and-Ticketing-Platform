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
