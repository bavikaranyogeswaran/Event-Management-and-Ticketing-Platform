# MVP Scope — Event Management & Ticketing Platform

**Version:** 1.0-draft · **Date:** 2026-07-16 · **Status:** Pending sign-off (Phase 1, Step 1.8)
**Related docs:** [requirements.md](requirements.md) · [assumptions.md](assumptions.md)

---

## 1. Product summary

A web platform where **organizers** create events and configure free or paid tickets, **admins** review and approve events before publication, **attendees** discover events and register or buy QR-code tickets, and **event staff** validate tickets at the venue with duplicate-proof check-in. Built for concerts, workshops, conferences, seminars, meetups, and small festivals in Sri Lanka (< 500 attendees per event). It is not a stadium-scale ticketing system.

## 2. Technology stack (frozen)

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3, Java 21, Maven — modular monolith (package-by-feature, 15 modules) |
| Frontend | React + TypeScript + Vite + Material UI (MUI) |
| Database | PostgreSQL + Flyway migrations (no auto-DDL) |
| Sessions / cache / rate limits | Redis (Memurai native on Windows) — Spring Session Data Redis, cache, Bucket4j |
| Async jobs | RabbitMQ (native Windows install) + transactional outbox in PostgreSQL |
| Object storage | Cloudinary — signed direct uploads, transformations, signed private URLs |
| Payments | Stripe **test mode** (dev-only; provider swap behind `PaymentGateway` port before live LKR payments) |
| Email | Gmail SMTP behind `EmailSender` port |
| API | REST + JSON under `/api/v1`, OpenAPI + Swagger UI |
| Testing | JUnit 5 + Mockito + AssertJ, Spring Boot Test + Testcontainers (Postgres/Redis/RabbitMQ), MockMvc |
| Excluded tooling | Docker, CI/CD, Kubernetes, Kafka, Elasticsearch, microservices, sharding |

## 3. In scope (MVP)

### Attendee
- Register, verify email, log in/out, reset password, manage profile
- Browse/search published events (category, date, text filters; keyset pagination)
- Order tickets: free (instant confirmation) and paid (Stripe test-mode hosted checkout)
- Per-ticket attendee names; idempotent, inventory-safe orders
- View order history and tickets; QR display; PDF ticket download
- Confirmation, cancellation, and pre-event reminder emails

### Organizer
- Organizer profile; create/edit event drafts (venue, dates, capacity, category, banner via Cloudinary)
- Configure ticket types (price LKR, quantity, sales window, max per order)
- Submit event for admin review; receive approve/reject decision by email; rework rejected drafts
- Cancel events (stops sales, notifies ticket holders)
- Dashboard: sales, revenue, remaining inventory, orders, attendee list, check-in counts
- Async CSV attendee export (private, signed URL, audited)

### Event staff
- Per-event staff assignment
- Check-in screen: QR scan or manual public-code entry
- Validate → check in exactly once; duplicate scans return "already used" with original time

### Administrator
- Event review queue: approve/reject with reason (FR-21)
- User moderation (suspend/activate — kills sessions); event moderation
- Audit log viewer; platform overview report

### Platform / cross-cutting
- 4 roles with service-layer ownership enforcement; Redis-backed sessions; CSRF + CORS
- Rate limiting (Redis) on auth, orders, checkout, validation, uploads, admin
- Zero-oversell inventory (conditional update), duplicate-proof payments/tickets/check-ins (unique constraints)
- Outbox → RabbitMQ email pipeline with retries and dead-letter queues
- Structured logs + request IDs, Actuator health, Micrometer metrics
- Audit logging of all sensitive actions
- Standard error envelope with stable error codes; OpenAPI/Swagger documentation
- Daily backups; documented + once-tested restore (RPO 24 h / RTO 8 h)

## 4. Out of scope (deferred post-MVP)

| Category | Deferred items |
|---|---|
| Ticketing | Reserved seating / seat maps, ticket transfers, waitlists, promotional codes, dynamic pricing, resale |
| Payments | Live payments (needs provider swap — R-01), automated/self-service refunds, payouts, split payments, service fees, multi-currency, tax/invoicing |
| Events | Calendar export, event cloning, public organizer profiles, recommendation engines |
| Notifications | SMS / WhatsApp; marketing emails |
| Clients | Native mobile apps; offline scanning |
| Infrastructure | Docker, CI/CD, Kubernetes, Kafka, microservices, Elasticsearch, sharding, read replicas, CDN beyond Cloudinary |
| Testing | Playwright E2E, k6 load tests (D12) |
| Other | Guest checkout, multi-language UI, advanced analytics |

Anything not listed in §3 is out of scope. New ideas go to this table, not into the MVP.

## 5. Milestones

### M1 — MVP milestone (free-ticket vertical slice)
Build order proves the core business workflow before payments:
1. Organizer creates a draft event with a free ticket type.
2. Organizer submits; admin approves; event is published and discoverable.
3. Attendee registers, orders a free ticket; inventory decreases safely.
4. QR ticket is generated and visible in the attendee dashboard.
5. Staff checks the ticket in; a duplicate scan is rejected.
6. Confirmation email delivered via outbox → RabbitMQ.

**Exit test:** concurrency integration test shows confirmed tickets never exceed configured quantity; duplicate check-in impossible.

### M2 — Release-ready milestone
M1 plus: Stripe test-mode paid checkout with verified webhook and order expiration · Cloudinary banner uploads · CSV export + PDF tickets · reminder emails · admin moderation + audit viewer · rate limiting · observability · full core test suite green · OpenAPI complete · security pass (IDOR/CSRF/webhook forgery/upload spoofing) · backup + restore drill done · ops docs written.

### M3 — Post-MVP (evidence-driven only)
Provider swap for live payments, E2E/load testing, and deferred features from §4 — each justified by real usage metrics, per the master plan's Phase 20 triggers.

## 6. Success criteria

- Every FR in [requirements.md](requirements.md) marked "In MVP" passes its acceptance criteria.
- Hard invariants hold under concurrent access (no oversell, no duplicate payment/ticket/check-in).
- NFR budgets met on the dev machine at target load (100 concurrent users).
- A failed order is traceable end-to-end via request ID without exposing secrets.
