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

No Docker — all services run natively on Windows.

### Prerequisites

| Requirement | Version used | Notes |
|---|---|---|
| Java (JDK) | 21 | `JAVA_HOME` set |
| Node.js | 20.19+ | with npm |
| PostgreSQL | 18 | **runs on port 5433** (non-default) |
| Redis | Memurai 4.x (Redis 7.2 compatible) | port 6379 |
| RabbitMQ | 4.3.x on Erlang 27.x | management UI on 15672; Erlang 28/29 unsupported |
| Cloudinary | free account | cloud name + API key/secret |

### One-time setup

1. **Databases** — as the `postgres` superuser (`psql -p 5433`):
   ```sql
   CREATE ROLE ticketing_app WITH LOGIN PASSWORD '<choose-one>';
   CREATE DATABASE ticketing OWNER ticketing_app;
   CREATE DATABASE ticketing_test OWNER ticketing_app;
   ```
2. **RabbitMQ management UI** — from the RabbitMQ Command Prompt (admin):
   `rabbitmq-plugins enable rabbitmq_management`, then restart the RabbitMQ service.
   Dev login: guest/guest (localhost only).
3. **Secrets** — copy [.env.example](.env.example) to `.env` in the repo root and fill in values.
   Raw or double-quoted values only; `.env` is gitignored.

### Run

```powershell
# backend — run from the repo root so .env is found — http://localhost:8080
java -jar backend\target\ticketing-backend-0.0.1-SNAPSHOT.jar
# or during development (also from the repo root):
backend\mvnw.cmd -f backend\pom.xml spring-boot:run

# frontend — http://localhost:5173 (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

Health check: http://localhost:8080/actuator/health → `{"status":"UP"}`.

## Status

- ✅ Phase 1 — Requirements frozen
- ✅ Phase 2 — Architecture designed
- ✅ Phase 3 — Repository & local environment setup
- ✅ Phase 4 — Database schema & Flyway migrations
- ✅ Phase 5 — Authentication & authorization
- 🔨 Phase 6 — Events, categories & ticket types (next)
