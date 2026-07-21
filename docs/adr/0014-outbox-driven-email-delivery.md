# ADR-0014 — Outbox-driven email delivery

**Status:** Accepted · 2026-07-22 (decisions D41, D42, D44)

## Context
Business transactions must not wait on, or be rolled back by, an email. A confirmed order, an approved event, a cancelled show — each already writes a job row inside its own transaction (the outbox), so the intent to notify commits atomically with the thing that caused it. What has been missing is everything after that row: getting it to a broker, sending it, and — the part that decides whether this is trustworthy — retrying the ones that fail without losing them or sending them twice. Email fails often and transiently: a rate limit, a DNS blip, an SMTP timeout. A design that drops those, or blocks a thread for two hours waiting to retry, is not acceptable.

## Decision
**The outbox row is the source of truth for delivery, not just for hand-off.** Each job carries a status, an attempt count, a `next_attempt_at`, and the last error. A scheduled relay claims jobs that are due (`PENDING`, `next_attempt_at` past), publishes each to RabbitMQ, and marks it `PUBLISHING`. The consumer renders and sends it, then marks it `SENT`; on failure it records the error, increments the attempt, and sets `next_attempt_at` to now plus the next backoff step (1m → 5m → 30m → 2h). Once the steps are exhausted the row becomes `DEAD` and is left for a human to see. A job stuck `PUBLISHING` past a grace period is returned to `PENDING` by the relay, so a crash between publish and send never strands it.

**RabbitMQ is transport, deliberately.** It decouples sending from the request and gives a real asynchronous consumer, but it does not own the retry schedule or the dead-letter state — Postgres does. This means the backoff needs no dead-letter-exchange plumbing or per-level TTL queues, and every retry, failure reason, and dead job is answerable with a SQL query and survives a broker restart. It is the same shape as the order-expiry sweep already proven in this codebase.

**Idempotency is by job key.** A job carries a stable key (`ORDER_CONFIRMATION:{orderId}`, `REMINDER:{eventId}:{userId}`, …). The relay publishes at least once, so the consumer may see a job twice; sending is guarded by the key so a duplicate delivery is a no-op rather than a second email.

**Sending sits behind an `EmailSender` port** (D42). The pipeline is built and tested against a capturing adapter, and Gmail SMTP is one isolated implementation chosen by configuration — the provider swap D6 anticipates touches one class.

**Verification and reset links carry a raw token that the auth store keeps only as a hash**, so the raw value lives in the job's payload until the email is sent and the row is cleared (D44). This is a brief, transient exposure of a single-use, short-lived token — a different thing from the durable hash-only store the tokens' security depends on.

## Consequences
- ✅ A business transaction never blocks on or is undone by email; the job commits with it and is delivered afterwards.
- ✅ Retry, backoff, failure reason, and dead-lettering are all in Postgres — inspectable, and intact across a broker restart.
- ✅ At-least-once publish plus key-guarded sending means a failure between steps costs a retry, never a lost or duplicated email.
- ⚠️ RabbitMQ earns less of its keep here than in a broker-driven design; it is justified by decoupling and a real consumer, not by owning retries. If volume ever outgrows a single relay, this is the seam to revisit.
- ⚠️ The relay polls, so a job waits up to one poll interval before its first send and before each retry — fine for transactional email, not for anything latency-critical.
- ⚠️ A raw token sits in a job payload until sent. Acceptable for a single-use token cleared on delivery; it would not be acceptable for a long-lived secret.
