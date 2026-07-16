# ADR-0005 — REST + JSON over GraphQL / gRPC

**Status:** Accepted · 2026-07-16

## Context
One browser client consumes the API. The resource model (events, orders, tickets) maps naturally to REST. GraphQL would add a schema layer, resolver complexity, and query-depth security concerns; gRPC targets service-to-service communication, not browsers. Neither solves an MVP requirement.

## Decision
REST + JSON under `/api/v1`. Standard error envelope `{timestamp, status, code, message, fieldErrors[], requestId}` with stable machine-readable codes. Keyset pagination response shape `{items[], page: {limit, nextCursor, hasMore}}`. OpenAPI generated from code (springdoc) with Swagger UI.

## Consequences
- ✅ Plain HTTP semantics: caching (ETags), status codes, rate limiting, and security tooling all work naturally.
- ✅ Swagger UI gives free interactive documentation.
- ⚠️ Some screens need multiple requests or purpose-built aggregate endpoints (e.g. organizer summary) — acceptable; we control both sides.
- Revisit when: multiple heterogeneous clients need flexible field selection.
