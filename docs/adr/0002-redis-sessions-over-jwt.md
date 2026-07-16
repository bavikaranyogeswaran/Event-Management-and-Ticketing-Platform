# ADR-0002 — Redis-backed server sessions over JWT

**Status:** Accepted · 2026-07-16

## Context
The only client is a browser SPA. JWTs would require refresh-token rotation, revocation lists, and client-side token storage — security-sensitive machinery that solves problems (stateless multi-service auth, native clients) this platform doesn't have. The source plan recommended JDBC sessions; since Redis is already in the stack (decision D7) for caching and rate limiting, Redis is the better session store.

## Decision
Use Spring Security with Spring Session Data Redis. Session ID rotated at login; sessions invalidated server-side at logout, password reset, and account suspension. Cookie: `Secure`, `HttpOnly`, `SameSite=Lax`. CSRF protection via cookie-to-header token on all writes.

## Consequences
- ✅ Instant revocation (logout/suspension kills the session server-side).
- ✅ No tokens in browser storage; cookie handling is battle-tested.
- ✅ Faster than JDBC sessions; no session traffic on PostgreSQL.
- ⚠️ Redis becomes a runtime dependency for login state (acceptable: it already backs rate limiting).
- ⚠️ CSRF protection is mandatory because cookies are sent automatically.
- Revisit when: native mobile apps or third-party API clients appear (token-based auth for those clients only).
