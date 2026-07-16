# ADR-0009 — PostgreSQL-first scaling: no sharding, no speculative caching

**Status:** Accepted · 2026-07-16

## Context
Expected volume (1–5 GB, ~5,000 tickets, ≤ 100 concurrent users) is tiny for a single PostgreSQL instance. Sharding, read replicas, search clusters, and broad caching would add operational complexity that solves no measured problem. Stale caches on inventory or payment state would be correctness bugs, not optimizations.

## Decision
One PostgreSQL instance is the system of record. Scaling order when pressure is *measured*: fix queries → indexes → remove N+1 → paginate → tune pool → vertical scale → HTTP caching → horizontal backend → read replica → partition audit/job tables. Sharding is explicitly rejected. Redis caching is limited to proven-safe data: active categories (10 min TTL, evict on admin change) and public event detail (short TTL, version-keyed). **Never cached:** inventory, order/payment/check-in status, permissions, tokens.

## Consequences
- ✅ Simple operations, full ACID semantics, one backup/restore story (RPO 24 h / RTO 8 h).
- ✅ Correctness-critical reads always hit the source of truth.
- ⚠️ A single database is a shared failure point — accepted: the CAP stance is consistency over availability (selling stops if the DB is down).
- Search stays `ILIKE` + indexes; full-text/dedicated search only after measured latency misses.
