# ADR-0003 — RabbitMQ + transactional outbox over a DB-only job table

**Status:** Accepted · 2026-07-16

## Context
The source plan proposed a PostgreSQL job table with polling workers to avoid running a broker. The user decided to include RabbitMQ (decision D7). A pure broker approach, however, loses the plan's key guarantee: a job must be recorded atomically with the business transaction (an email job must exist if and only if the order committed). Publishing to RabbitMQ inside a DB transaction cannot be atomic.

## Decision
Combine both: a transactional **outbox** table (`outbox_jobs`) written in the same PostgreSQL transaction as the business change, plus a relay (`@Scheduled`, 2 s, `FOR UPDATE SKIP LOCKED`, publisher confirms) that publishes rows to RabbitMQ. Consumers handle email, CSV export, and cleanup; retries use TTL wait-queues (1 m → 5 m → 30 m → 2 h) with dead-letter queues after 5 attempts. Every job carries a unique idempotency key (e.g. `ORDER_CONFIRMATION:{orderId}`); consumers check state before side effects.

## Consequences
- ✅ Exactly the plan's atomicity guarantee, with broker-grade throughput and backoff/DLQ semantics.
- ✅ Broker downtime is harmless — jobs wait in PostgreSQL; business transactions never block on RabbitMQ.
- ✅ Consumers scale independently of request threads.
- ⚠️ One more service to install and operate (Erlang + RabbitMQ on Windows).
- ⚠️ ~2 s relay latency added to job pickup (irrelevant for email/export).
- Duplicate delivery is possible (at-least-once); idempotent consumers are therefore mandatory, not optional.
