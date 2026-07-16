# ADR-0010 — Integration tests against local PostgreSQL instead of Testcontainers

**Status:** Accepted · 2026-07-16

## Context
The source plan specified Testcontainers for integration tests, but Testcontainers requires a container runtime and this project explicitly excludes Docker. The dev machine already runs the full service stack natively as Windows services: PostgreSQL 18 (port 5433), Memurai (Redis-compatible, 6379), and RabbitMQ (5672). A dedicated `ticketing_test` database exists alongside the dev `ticketing` database, both owned by `ticketing_app`.

## Decision
Integration tests run against the **local native services**:

- **Database:** `ticketing_test` on the local PostgreSQL. The migration test (`MigrationIntegrationTest`) executes `flyway.clean()` + `migrate()` once per run, proving the empty-database migration path every time. Spring integration tests extend `AbstractIntegrationTest` (`@SpringBootTest` + `@ActiveProfiles("test")`), whose Flyway validates against the already-migrated schema. Data-touching tests must run transactionally and roll back.
- **Redis / RabbitMQ:** tests connect to the real local Memurai and RabbitMQ — no mocks, no embedded substitutes.
- **Credentials:** tests reuse the repo-root `.env` (Surefire sets `springdotenv.directory` to the repo root; non-Spring tests read it via dotenv-java in `TestEnv`).

## Consequences
- ✅ No Docker dependency; highest-fidelity testing against the exact engines used in development.
- ✅ Empty-DB migration path proven on every test run (`clean-disabled: false` in the test profile only).
- ⚠️ Tests require the three Windows services to be running — they are auto-start services, so this is normally invisible.
- ⚠️ Test parallelism across JVMs is unsafe (shared database); Surefire's single-JVM default is fine.
- ⚠️ `ticketing_test` contents are disposable by definition — never point tests at the dev `ticketing` database.
- Revisit if: CI infrastructure appears (Testcontainers becomes viable there) or tests need isolated parallel databases.
