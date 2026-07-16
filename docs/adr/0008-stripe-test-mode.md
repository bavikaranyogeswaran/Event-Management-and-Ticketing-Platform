# ADR-0008 — Stripe test mode with a provider-swap exit strategy

**Status:** Accepted · 2026-07-16 (decision D5)

## Context
The platform targets Sri Lanka (LKR). Stripe offers the best sandbox, documentation, and webhook tooling for building the paid-checkout flow — but has no live merchant support in Sri Lanka, so it can never process real LKR payments for this platform (risk R-01). Local providers (e.g. PayHere) support LKR but have weaker developer experience.

## Decision
Build paid checkout against **Stripe test mode** for the MVP, strictly behind the `PaymentGateway` port: `createCheckoutSession(...)` and `parseAndVerify(webhook)`. No Stripe SDK types, IDs, or assumptions outside the `payment` module's adapter. The domain flow (pending order → reserve inventory → hosted checkout → verified webhook → confirm once) is provider-agnostic. Before any real payment, swap the adapter to a Sri Lanka-capable provider.

## Consequences
- ✅ Fastest, best-documented path to a correct checkout + webhook implementation.
- ✅ The webhook correctness rules (signature, replay protection, amount match, exactly-once confirmation) transfer to any provider.
- ⚠️ **Live payments are impossible without an adapter swap** — recorded as a release blocker for production payments.
- ⚠️ Provider-specific webhook payloads mean the adapter swap needs its own verification test suite when it happens.
