# ADR-0001 — Modular monolith over microservices

**Status:** Accepted · 2026-07-16

## Context
One developer builds and operates the entire platform. Target scale is small (≤ 100 concurrent users, 30–50 RPS bursts, ~5,000 tickets). The critical workflows (inventory, payment confirmation, check-in) need ACID transactions across entities. Microservices would add network hops, distributed consistency, service discovery, cross-service security, and multiplied deployment work — with no current problem they solve.

## Decision
Build a single Spring Boot deployment structured as a modular monolith: 15 feature packages (`auth, user, organizer, event, tickettype, order, payment, ticket, checkin, notification, file, reporting, admin, audit, shared`), each owning its controllers, DTOs, services, domain rules, repositories, and tests. Cross-module access goes through application services only — never another module's repository or entity.

## Consequences
- ✅ Local method calls and single-database ACID transactions for the correctness-critical flows.
- ✅ One build, one deployment, one log stream — operable by one person.
- ✅ Module discipline keeps future extraction possible (candidates: notifications, payments, reporting, search).
- ⚠️ All modules deploy together; one bad module can affect the process.
- ⚠️ Boundaries are enforced by convention (ArchUnit optional later), so discipline is required.
- Revisit when: multiple teams need independent releases, or a module shows measured independent scaling pressure.
