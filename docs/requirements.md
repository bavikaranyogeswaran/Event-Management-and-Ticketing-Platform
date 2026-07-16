# Requirements — Event Management & Ticketing Platform

**Version:** 1.0-draft · **Date:** 2026-07-16 · **Status:** Pending sign-off (Phase 1, Step 1.8)
**Related docs:** [assumptions.md](assumptions.md) · [mvp-scope.md](mvp-scope.md)

Platform: Spring Boot 3 (Java 21) modular monolith · React + TypeScript + Vite (MUI) · PostgreSQL + Flyway · Redis (Memurai) · RabbitMQ · Cloudinary · Stripe test mode · Gmail SMTP. No Docker, no CI/CD.

---

## 1. Functional Requirements Overview

| ID | Requirement | Priority | In MVP |
|----|-------------|----------|--------|
| FR-01 | User registration | Must | ✅ |
| FR-02 | Authentication (sessions) | Must | ✅ |
| FR-03 | Password recovery + email verification | Should | ✅ |
| FR-04 | Profile management | Should | ✅ |
| FR-05 | Role management | Must | ✅ |
| FR-06 | Event management (lifecycle) | Must | ✅ |
| FR-07 | Event discovery | Must | ✅ |
| FR-08 | Ticket types | Must | ✅ |
| FR-09 | Order creation (inventory-safe) | Must | ✅ |
| FR-10 | Free registration | Must | ✅ |
| FR-11 | Paid checkout (Stripe test mode) | Should | ✅ |
| FR-12 | Ticket generation (QR) | Must | ✅ |
| FR-13 | Ticket viewing + PDF download | Must | ✅ |
| FR-14 | Check-in (duplicate-proof) | Must | ✅ |
| FR-15 | Organizer dashboard | Must | ✅ |
| FR-16 | CSV attendee export | Should | ✅ |
| FR-17 | Administration & moderation | Must | ✅ |
| FR-18 | Notifications (email, incl. reminders) | Should | ✅ |
| FR-19 | File uploads (Cloudinary) | Should | ✅ |
| FR-20 | Reporting & sales summaries | Should | ✅ |
| FR-21 | Event approval workflow | Must | ✅ |

---

## 2. Detailed Requirements & Acceptance Criteria

### FR-01 — User registration
A visitor can create an account with email, password, and display name.

**Acceptance criteria**
- Email is normalized (lowercased/trimmed) and unique; duplicate registration returns a clear error without revealing account existence beyond necessity.
- Password is stored only as an adaptive hash (BCrypt/Argon2) with unique salt; the raw password is never logged or persisted.
- Input is validated server-side (email format, password length/strength, name length limits).
- A verification email is sent on registration (see FR-03).
- Registration is rate-limited per IP.

### FR-02 — Authentication
Users log in and out with email + password; sessions are server-side.

**Acceptance criteria**
- Login verifies account status (active/suspended) and password hash; failures return a generic message.
- Session is created server-side (Spring Session Data Redis), rotated on login, invalidated on logout.
- Browser receives a secure, HTTP-only, SameSite cookie; no tokens in localStorage.
- Sessions expire after a configured idle timeout.
- `GET /auth/session` reports the current authenticated identity and roles.
- Login attempts are rate-limited per IP and per account.

### FR-03 — Password recovery + email verification
Users can reset a forgotten password and verify their email address.

**Acceptance criteria**
- Reset and verification tokens are single-use, expire (≤ 60 min), and only their hashes are stored.
- Password reset invalidates all existing sessions for the account.
- The forgot-password endpoint responds identically whether or not the email exists.
- Recovery endpoints are rate-limited.

### FR-04 — Profile management
Users manage their own profile.

**Acceptance criteria**
- `GET/PATCH /users/me` reads/updates display name, phone, profile image (via FR-19).
- `DELETE /users/me` soft-deletes the account; financial/ticket records are retained with explicit statuses.
- A user can never read or modify another user's profile.

### FR-05 — Role management
Four roles: ATTENDEE, ORGANIZER, STAFF, ADMIN.

**Acceptance criteria**
- Every user has ≥ 1 role; a user may hold multiple roles (e.g., organizer who buys tickets).
- Ownership checks execute in application services (not only controllers): users see only their orders/tickets; organizers only owned events; staff only assigned events.
- The role matrix (browse/buy: all; create-edit events: organizer-owned + admin; check-in: owned/assigned + admin; suspend users & view audit logs: admin only) is enforced by tests.
- All admin actions are audit-logged.

### FR-06 — Event management (lifecycle)
Organizers create and manage events through a controlled lifecycle.

**Acceptance criteria**
- Lifecycle: `DRAFT → PENDING_REVIEW → PUBLISHED → CANCELLED / COMPLETED`, plus `REJECTED → DRAFT` (rework). Transitions are validated server-side (see FR-21 for review).
- Event fields: slug, title, description, category, physical/online type, venue + location (required for physical), timezone, start/end, registration open/close, capacity, banner image.
- Validation: end after start; registration closes before event start; positive capacity; submission for review requires ≥ 1 valid ticket type.
- Edits use optimistic locking (version column); concurrent conflicting edits fail safely.
- Cancellation sets status, stops sales immediately, and enqueues cancellation notifications to ticket holders.

### FR-07 — Event discovery
Public users browse and search published events.

**Acceptance criteria**
- `GET /events` lists only PUBLISHED, upcoming events with keyset pagination (default 20 / max 50, sort `startsAt,id`).
- Filters: category, date range, text search on title; sort fields are allowlisted.
- `GET /events/{id}` and `GET /events/slug/{slug}` return public detail incl. available ticket types; drafts/pending/rejected events are never exposed publicly.
- Event list p95 < 500 ms; event detail p95 < 300 ms.

### FR-08 — Ticket types
Organizers configure ticket types per event.

**Acceptance criteria**
- Fields: name, description, price (≥ 0, LKR), total quantity, sold counter, max per order (> 0), sales window, status.
- `quantity_sold ≤ quantity_total` is enforced by a DB check constraint.
- Ticket types outside their sales window or sold out are shown but not purchasable.
- Price changes never alter existing orders (order items copy name + unit price).

### FR-09 — Order creation (inventory-safe)
Authenticated attendees create orders with server-side pricing and no overselling.

**Acceptance criteria**
- `POST /orders` requires an `Idempotency-Key` header; retries with the same key return the original order (`unique (user_id, idempotency_key)`).
- Totals are computed server-side from persisted prices; client-sent amounts are ignored.
- Inventory is decremented with a conditional update (`WHERE quantity_sold + :n <= quantity_total`); zero affected rows → 409 `TICKET_INVENTORY_EXHAUSTED`.
- Per-order limits and sales windows are enforced: `ORDER_LIMIT_EXCEEDED`, `EVENT_NOT_ON_SALE`, `TICKET_TYPE_NOT_AVAILABLE`.
- Each ticket in an order carries an attendee name (buyer may enter different names per ticket).
- **Invariant (tested with parallel-purchase integration tests): confirmed quantity never exceeds configured quantity.**

### FR-10 — Free registration
Free-ticket orders confirm immediately in one transaction.

**Acceptance criteria**
- One ACID transaction: validate → decrement inventory → create order + items (status CONFIRMED) → create tickets → write outbox notification row → commit.
- Partial failure rolls back everything, including the inventory decrement.
- Confirmation email job is delivered via the outbox → RabbitMQ pipeline after commit.

### FR-11 — Paid checkout (Stripe test mode)
Paid orders use a Stripe-hosted checkout session; the backend never touches card data.

**Acceptance criteria**
- `POST /orders/{id}/checkout` creates a Stripe Checkout Session for a PENDING_PAYMENT order and returns the redirect URL.
- Pending orders reserve inventory and expire (`expires_at`, ~15 min); an expiration sweep cancels them and returns inventory atomically.
- Webhook `POST /webhooks/payments/stripe`: signature verified → order/payment locked → duplicate provider events ignored (`unique (provider, provider_payment_id)`) → amount + currency verified against the order → payment + order confirmed → tickets created exactly once → outbox row → commit.
- Provider failure never fakes success: order stays pending and can be retried.
- Stripe is dev/test only; live LKR payments require an adapter swap behind the `PaymentGateway` port (see assumptions R-01).

### FR-12 — Ticket generation
Confirmed orders produce secure QR tickets.

**Acceptance criteria**
- Each ticket has a unique random public code (human-enterable) and a cryptographically random validation token; **only the token hash is stored**.
- The QR encodes the raw validation token; tokens are never logged.
- Tickets are created inside the order-confirmation transaction — never duplicated on retry or webhook replay.
- Ticket statuses: VALID, USED, CANCELLED.

### FR-13 — Ticket viewing + PDF download
Owners view their tickets and download PDFs.

**Acceptance criteria**
- `GET /users/me/tickets` (keyset, `issuedAt,id`) and `GET /tickets/{id}` are owner-only; IDOR attempts return 404/403.
- `GET /tickets/{id}/qr` renders the QR for the owner only.
- A server-generated PDF ticket (event, attendee name, public code, QR) is downloadable by the owner.

### FR-14 — Check-in
Staff validate tickets and check attendees in exactly once.

**Acceptance criteria**
- `POST /check-ins/validate` (dry-run) and `POST /check-ins` resolve the ticket by token hash or public code, scoped to the staff member's assigned event (organizer: owned events; admin: any).
- Check-in transaction: validate event + ticket state → insert check-in row (**unique `ticket_id` constraint**) → mark ticket USED → commit.
- A duplicate scan returns a structured "already used" response with the original check-in time — never a second success.
- Wrong-event, cancelled, or forged tickets are rejected with distinct error codes.
- Check-in p95 < 500 ms; concurrent duplicate scans are covered by integration tests.

### FR-15 — Organizer dashboard
Organizers monitor sales, inventory, and attendance for owned events.

**Acceptance criteria**
- `GET /organizer/events` (keyset) lists owned events with status and sales counts.
- `GET /organizer/events/{id}/summary` returns tickets sold/remaining per type, revenue (LKR), order counts by status, and check-in counts; p95 < 1 s.
- `GET /organizer/events/{id}/orders` and `/attendees` are keyset-paginated (attendees default 50 / max 100).
- All endpoints enforce ownership.

### FR-16 — CSV attendee export
Organizers export attendee lists asynchronously.

**Acceptance criteria**
- Export request enqueues a RabbitMQ job; the generated CSV is stored as a **private** Cloudinary raw asset.
- Download uses a short-lived signed URL; the asset is never publicly accessible.
- Every export (who, which event, when) is audit-logged.
- Export contains only necessary fields (name, ticket type, code, check-in status) — no unnecessary PII.

### FR-17 — Administration & moderation
Admins moderate users and events and review audits.

**Acceptance criteria**
- `GET /admin/users` (offset, default 25/max 100) with status filters; `PATCH /admin/users/{id}/status` suspends/activates (suspension kills sessions).
- `GET /admin/events` incl. the PENDING_REVIEW queue; `PATCH /admin/events/{id}/status` for moderation.
- `GET /admin/audit-logs` (keyset, `createdAt,id`).
- Every admin mutation writes an audit entry; admin endpoints are rate-limited.

### FR-18 — Notifications (email)
Transactional and scheduled email via Gmail SMTP behind the `EmailSender` port.

**Acceptance criteria**
- Emails: registration verification, password reset, order confirmation + tickets, event cancellation, approval decision (approved/rejected), **pre-event reminder to ticket holders (e.g., 24 h before start)**.
- All email sending is asynchronous (outbox → RabbitMQ consumer); email failure never rolls back a business transaction.
- Retries with backoff (1 m → 5 m → 30 m → 2 h), then dead-letter; consumers are idempotent via job keys (e.g., `ORDER_CONFIRMATION:{orderId}`).
- Reminder scheduler enqueues exactly one reminder per ticket holder per event.

### FR-19 — File uploads (Cloudinary)
Event banners and profile images upload directly to Cloudinary with backend authorization.

**Acceptance criteria**
- Flow: `POST /files/upload-requests` (backend validates ownership, purpose, MIME, size; creates PENDING metadata; returns signed upload params with a random public_id) → browser uploads to Cloudinary → `POST /files/{id}/complete` (backend verifies via Admin API and attaches).
- Allowed: JPEG/PNG/WebP; banner ≤ 5 MB, profile/thumbnail ≤ 2 MB; server-side MIME verification; original filename never used as storage key.
- Public assets (published event banners) via CDN with Cloudinary transformations (delivery target < 500 KB); private assets (exports) via signed, expiring URLs only.
- Deletion: detach reference → mark metadata deleted → async Cloudinary destroy with retry.

### FR-20 — Reporting & sales summaries
Basic aggregates for organizers and admins.

**Acceptance criteria**
- Per-event: tickets sold, revenue, orders by status, attendance rate — via optimized aggregate queries/projections (no N+1).
- Admin platform overview: totals of users, events by status, orders, tickets.
- All report endpoints paginate or aggregate — no unbounded lists.

### FR-21 — Event approval workflow
Events require admin approval before publication (decision D2).

**Acceptance criteria**
- Organizer submits a valid draft → `PENDING_REVIEW`; the event remains publicly invisible.
- Admin approves → `PUBLISHED` (publication timestamp set) or rejects with a reason → `REJECTED`; organizer can rework and resubmit (`REJECTED → DRAFT`).
- Approval/rejection notifies the organizer by email (FR-18) and is audit-logged.
- Editing a PUBLISHED event's critical fields (dates, venue, capacity) — MVP rule: allowed without re-review, changes audit-logged (see assumptions A-13).

---

## 3. Non-Functional Requirements (accepted as documented — D11)

### 3.1 Performance budgets
| Operation | Target (p95) |
|---|---|
| Event list API | < 500 ms |
| Event detail API | < 300 ms |
| Order creation | < 1 s |
| Check-in | < 500 ms |
| Organizer summary | < 1 s |
| API reads (general) | < 500 ms |
| API writes (general) | < 1 s |
| Public page usable | < 2 s |

All unbounded lists are paginated (default 20–50, max 100).

### 3.2 Scalability targets
500–1,000 registered users · up to 100 concurrent users · 30–50 RPS bursts · ~5,000 tickets · 1–5 GB structured data. Single backend instance + single PostgreSQL; scale vertically first.

### 3.3 Availability & correctness
- Best-effort ~99% availability; planned maintenance acceptable.
- **Hard invariants:** no ticket overselling; no duplicate payment confirmation; no duplicate ticket creation; no duplicate check-in.
- CAP stance: consistency over availability for inventory/payment/check-in — if the database is down, selling stops.

### 3.4 Security
- HTTPS everywhere; secure/HTTP-only/SameSite session cookies; CSRF tokens on cookie-authenticated writes; restricted CORS origins.
- Adaptive password hashing; hashed single-use tokens; generic auth failures.
- Server-side authorization + ownership checks in services (IDOR defense).
- Rate limiting (Bucket4j + Redis, per-IP and per-account): login, registration, password recovery, order creation, checkout, ticket validation, upload authorization, admin endpoints.
- Stripe webhook signature verification; replay protection via unique provider event/payment IDs.
- File safety: MIME/size verification, random storage keys, no active content, private assets via signed URLs.
- Secrets outside source control; parameterized queries only; allowlisted sort fields; escaped frontend output; CSP.
- Audit trail for auth events, publications/approvals, cancellations, status changes, exports, check-ins, admin actions.

### 3.5 Maintainability
Modular monolith, package-by-feature (15 modules); DTOs separated from entities; Flyway-only migrations (no auto-DDL); consistent error envelope `{timestamp, status, code, message, fieldErrors[], requestId}`; ADRs for major decisions.

### 3.6 Observability
Structured JSON logs with correlation/request IDs; Actuator liveness + readiness (DB + migrations, not email); Micrometer metrics (latency, error rate, login failures, orders, payment failures, inventory conflicts, tickets, check-in failures, DB pool, queue depth/age, dead letters); **never logged:** passwords, cookies, QR/reset tokens, card data, full provider payloads.

### 3.7 Compliance & recovery
Minimal PII collection; no card data stored; **RPO 24 h / RTO 8 h** — daily logical backups (7 daily + 4 weekly retained), restore procedure documented and tested once before release.

### 3.8 Testing (Core-only — D12)
- Unit tests (JUnit 5, Mockito, AssertJ): domain rules, totals, state transitions — ~80%+ on domain/application services.
- Integration tests (Spring Boot Test + Testcontainers: PostgreSQL, Redis, RabbitMQ): repositories, migrations (empty + upgrade), transactions, consumers.
- API tests (MockMvc): contracts, error codes, authn/authz, pagination.
- **Concurrency integration tests (retained despite core-only, to prove §3.3 invariants):** parallel purchases vs. limited stock, parallel duplicate check-ins, webhook replay.
- Out: Playwright E2E, k6 load tests (deferred post-MVP).
