# Event Management and Ticketing Platform

A web platform where **organizers** create events and configure free or paid tickets, **admins** review and approve events before publication, **attendees** discover events and buy QR-code tickets, and **event staff** validate tickets at the venue with duplicate-proof check-in.

Built for concerts, workshops, conferences, seminars, meetups, and small festivals in Sri Lanka (LKR, < 500 attendees per event).

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3 · Java 21 · Maven — modular monolith |
| Frontend | React · TypeScript · Vite · Material UI |
| Database | PostgreSQL · Flyway migrations |
| Sessions / cache / rate limits | Redis (Memurai on Windows) |
| Async jobs | RabbitMQ + transactional outbox |
| Object storage | Cloudinary (signed direct uploads) |
| Payments | Stripe (test mode, dev-only) |
| Email | Gmail SMTP |
| API | REST + JSON (`/api/v1`) · OpenAPI / Swagger UI |

## Repository layout

```text
├── backend/    # Spring Boot application (Phase 3.6)
├── frontend/   # React SPA (Phase 3.7)
└── docs/       # requirements, architecture, ADRs, use cases, API contracts
```

## Documentation

| Document | Contents |
|---|---|
| [docs/requirements.md](docs/requirements.md) | 21 functional requirements with acceptance criteria + NFRs |
| [docs/assumptions.md](docs/assumptions.md) | Decision register, assumptions, risks, operational defaults |
| [docs/mvp-scope.md](docs/mvp-scope.md) | Frozen MVP scope, milestones M1–M3 |
| [docs/architecture.md](docs/architecture.md) | System design: modules, ports, security, data model, async design |
| [docs/use-cases.md](docs/use-cases.md) | 11 core use cases with sequence diagrams |
| [docs/adr/](docs/adr/) | Architecture decision records (0001–0009) |
| [docs/api/vertical-slice.md](docs/api/vertical-slice.md) | API contract for the free-ticket vertical slice (M1) |

## Local setup

> Filled in at Phase 3.8 — requires PostgreSQL, Redis (Memurai), RabbitMQ, and a Cloudinary account. No Docker used.

## Status

- ✅ Phase 1 — Requirements frozen
- ✅ Phase 2 — Architecture designed
- 🔨 Phase 3 — Repository & local environment setup (in progress)
