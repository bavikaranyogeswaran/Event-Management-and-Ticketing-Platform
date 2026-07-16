# API Contract — Free-Ticket Vertical Slice (Milestone M1)

**Version:** 1.0-draft · **Date:** 2026-07-16 · **Status:** Pending sign-off (Phase 2, Step 2.9)
**Related docs:** [architecture.md](../architecture.md) · [use-cases.md](../use-cases.md) · [mvp-scope.md](../mvp-scope.md)

Contract for every endpoint needed by milestone M1 (organizer creates event → admin approves → attendee orders free ticket → staff checks in). The authoritative OpenAPI spec is generated from code later (springdoc, Phase 18); this document is the build target until then.

---

## 1. Conventions

- **Base path:** `/api/v1` (all paths below are relative to it)
- **Auth:** Redis session cookie; CSRF token header (`X-XSRF-TOKEN`) required on every write
- **Request ID:** every response carries `X-Request-Id`
- **Content type:** `application/json` (except `/tickets/{id}/qr` → `image/png`)

**Error envelope (all non-2xx):**
```json
{
  "timestamp": "2026-07-16T12:00:00Z",
  "status": 409,
  "code": "TICKET_INVENTORY_EXHAUSTED",
  "message": "The selected ticket quantity is no longer available.",
  "fieldErrors": [ { "field": "items[0].quantity", "message": "must be at most 4" } ],
  "requestId": "req_01J..."
}
```

**Pagination (keyset):** request `?limit=20&cursor=...`; response:
```json
{ "items": [], "page": { "limit": 20, "nextCursor": "b3BhcXVl...", "hasMore": true } }
```

## 2. Endpoint inventory

| # | Method & path | Role | Purpose |
|---|---|---|---|
| 1 | `POST /auth/register` | public | create account |
| 2 | `POST /auth/login` | public | start session |
| 3 | `POST /auth/logout` | any | end session |
| 4 | `GET /auth/session` | any | current identity + roles |
| 5 | `GET /categories` | public | active categories |
| 6 | `GET /events` | public | published events (keyset, filters) |
| 7 | `GET /events/{eventId}` | public | event detail |
| 8 | `GET /events/{eventId}/ticket-types` | public | purchasable ticket types |
| 9 | `POST /organizer/events` | organizer | create draft |
| 10 | `PATCH /organizer/events/{eventId}` | organizer (owner) | edit draft/rejected |
| 11 | `POST /organizer/events/{eventId}/ticket-types` | organizer (owner) | add ticket type |
| 12 | `POST /organizer/events/{eventId}/submit` | organizer (owner) | DRAFT → PENDING_REVIEW |
| 13 | `GET /admin/events?status=PENDING_REVIEW` | admin | review queue |
| 14 | `POST /admin/events/{eventId}/review` | admin | approve / reject |
| 15 | `POST /orders` | attendee | create (free) order — idempotent |
| 16 | `GET /orders/{orderId}` | owner | order detail |
| 17 | `GET /users/me/tickets` | attendee | my tickets (keyset) |
| 18 | `GET /tickets/{ticketId}` | owner | ticket detail |
| 19 | `GET /tickets/{ticketId}/qr` | owner | QR PNG |
| 20 | `POST /check-ins/validate` | staff/organizer/admin | dry-run validation |
| 21 | `POST /check-ins` | staff/organizer/admin | atomic check-in |

## 3. Key contracts

### 3.1 `POST /auth/register`
```json
// request
{ "email": "asha@example.com", "password": "S3cure-Pass!", "displayName": "Asha" }
// 201
{ "id": "usr_uuid", "email": "asha@example.com", "displayName": "Asha", "emailVerified": false }
```
Errors: `400 VALIDATION_FAILED` · `409 EMAIL_ALREADY_REGISTERED`* · `429 RATE_LIMITED`
*Response body identical in shape; message avoids confirming account existence in user-visible copy.

### 3.2 `POST /auth/login`
```json
// request
{ "email": "asha@example.com", "password": "S3cure-Pass!" }
// 200 (+ Set-Cookie: SESSION=...; HttpOnly; Secure; SameSite=Lax)
{ "userId": "usr_uuid", "displayName": "Asha", "roles": ["ATTENDEE"] }
```
Errors: `401 INVALID_CREDENTIALS` (generic, also for suspended) · `429 RATE_LIMITED`

### 3.3 `GET /events` (public search)
Query params: `limit` (≤ 50), `cursor`, `categoryId`, `from`, `to`, `q` (title search). Sort fixed: `startsAt,id`.
```json
// 200
{
  "items": [{
    "id": "evt_uuid", "slug": "colombo-tech-meetup-aug",
    "title": "Colombo Tech Meetup", "categoryName": "Meetups",
    "eventType": "PHYSICAL", "venueName": "Trace Expert City",
    "city": "Colombo", "startsAt": "2026-08-20T18:00:00+05:30",
    "bannerUrl": "https://res.cloudinary.com/.../c_fill,w_800/evt_banner.webp",
    "priceFrom": 0.00, "currency": "LKR"
  }],
  "page": { "limit": 20, "nextCursor": "…", "hasMore": true }
}
```

### 3.4 `POST /organizer/events`
```json
// request
{
  "title": "Colombo Tech Meetup", "description": "Monthly meetup…",
  "categoryId": "cat_uuid", "eventType": "PHYSICAL",
  "venueName": "Trace Expert City", "addressLine": "Maradana Rd", "city": "Colombo",
  "timezone": "Asia/Colombo",
  "startsAt": "2026-08-20T18:00:00+05:30", "endsAt": "2026-08-20T21:00:00+05:30",
  "registrationOpensAt": "2026-07-20T00:00:00+05:30",
  "registrationClosesAt": "2026-08-20T12:00:00+05:30",
  "capacity": 150
}
// 201 → { "id": "evt_uuid", "status": "DRAFT", "slug": "colombo-tech-meetup", "version": 0 }
```
Errors: `422 EVENT_DATES_INVALID` · `422 VENUE_REQUIRED` · `400 VALIDATION_FAILED`

### 3.5 `POST /organizer/events/{eventId}/ticket-types`
```json
// request
{ "name": "General Admission", "price": 0.00, "quantityTotal": 100, "maxPerOrder": 4,
  "salesStartAt": "2026-07-20T00:00:00+05:30", "salesEndAt": "2026-08-20T12:00:00+05:30" }
// 201 → { "id": "tt_uuid", "status": "ACTIVE", "quantitySold": 0 }
```

### 3.6 `POST /organizer/events/{eventId}/submit`
No body. `200 → { "id": "evt_uuid", "status": "PENDING_REVIEW", "submittedAt": "…" }`
Errors: `422 PUBLICATION_RULES_FAILED` (with fieldErrors) · `409 INVALID_STATE_TRANSITION`

### 3.7 `POST /admin/events/{eventId}/review`
```json
// request (reason required when REJECTED)
{ "decision": "APPROVED" }
{ "decision": "REJECTED", "reason": "Venue details incomplete." }
// 200 → { "id": "evt_uuid", "status": "PUBLISHED", "publishedAt": "…" }
```
Errors: `409 INVALID_STATE_TRANSITION` · `400 REASON_REQUIRED`

### 3.8 `POST /orders` — required header `Idempotency-Key: <uuid>`
```json
// request
{
  "eventId": "evt_uuid",
  "items": [ { "ticketTypeId": "tt_uuid", "quantity": 2 } ],
  "attendees": [ { "name": "Asha Perera" }, { "name": "Nuwan Silva" } ]
}
// 201 (free order confirms instantly)
{
  "id": "ord_uuid", "orderNumber": "ORD-2026-000042", "status": "CONFIRMED",
  "currency": "LKR", "subtotal": 0.00, "grandTotal": 0.00,
  "tickets": [
    { "id": "tkt_uuid1", "publicCode": "TCK-7F3K-9Q2M", "attendeeName": "Asha Perera", "status": "VALID" },
    { "id": "tkt_uuid2", "publicCode": "TCK-2B8X-4W7N", "attendeeName": "Nuwan Silva", "status": "VALID" }
  ]
}
```
Rules: attendees.length == total quantity; totals always server-computed; same key retried → `200` with the original order.
Errors: `409 EVENT_NOT_ON_SALE` · `409 TICKET_TYPE_NOT_AVAILABLE` · `422 ORDER_LIMIT_EXCEEDED` · `409 TICKET_INVENTORY_EXHAUSTED` · `409 IDEMPOTENCY_CONFLICT` (same key, different payload) · `428 IDEMPOTENCY_KEY_REQUIRED`

### 3.9 `GET /tickets/{ticketId}/qr`
`200` → `image/png` (QR of the raw validation token). Owner only; others → `404 RESOURCE_NOT_FOUND`.

### 3.10 `POST /check-ins/validate` (dry-run) and `POST /check-ins`
```json
// request (one of token | publicCode)
{ "eventId": "evt_uuid", "token": "raw-qr-token" }
{ "eventId": "evt_uuid", "publicCode": "TCK-7F3K-9Q2M" }

// validate 200
{ "ticketId": "tkt_uuid", "attendeeName": "Asha Perera",
  "ticketTypeName": "General Admission", "ticketStatus": "VALID", "checkInAllowed": true }

// check-in 200
{ "ticketId": "tkt_uuid", "attendeeName": "Asha Perera",
  "checkedInAt": "2026-08-20T18:05:12+05:30", "method": "QR" }
```
Errors: `404 TICKET_NOT_FOUND` · `422 WRONG_EVENT` · `422 TICKET_CANCELLED` · `409 ALREADY_CHECKED_IN` (body includes original `checkedInAt`) · `403 NOT_ASSIGNED_TO_EVENT`

## 4. Error-code registry (M1)

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean-validation failure (see fieldErrors) |
| `INVALID_CREDENTIALS` | 401 | Login failed (generic) |
| `AUTHENTICATION_REQUIRED` | 401 | No/expired session |
| `FORBIDDEN` | 403 | Role lacks permission |
| `NOT_ASSIGNED_TO_EVENT` | 403 | Staff not assigned to this event |
| `RESOURCE_NOT_FOUND` | 404 | Missing **or not owned** (anti-enumeration) |
| `TICKET_NOT_FOUND` | 404 | Unknown token/code |
| `EMAIL_ALREADY_REGISTERED` | 409 | Duplicate registration |
| `INVALID_STATE_TRANSITION` | 409 | Lifecycle rule violated |
| `EVENT_NOT_ON_SALE` | 409 | Outside registration/sales window |
| `TICKET_TYPE_NOT_AVAILABLE` | 409 | Inactive/out-of-window ticket type |
| `TICKET_INVENTORY_EXHAUSTED` | 409 | Conditional inventory update affected 0 rows |
| `IDEMPOTENCY_CONFLICT` | 409 | Key reused with different payload |
| `ALREADY_CHECKED_IN` | 409 | Duplicate check-in attempt |
| `CONFLICT_RETRY` | 409 | Optimistic-lock conflict |
| `ORDER_LIMIT_EXCEEDED` | 422 | Quantity > maxPerOrder |
| `PUBLICATION_RULES_FAILED` | 422 | Submit-for-review validation failed |
| `EVENT_DATES_INVALID` | 422 | Incoherent dates |
| `VENUE_REQUIRED` | 422 | Physical event without venue |
| `WRONG_EVENT` | 422 | Ticket belongs to another event |
| `REASON_REQUIRED` | 400 | Rejection without reason |
| `IDEMPOTENCY_KEY_REQUIRED` | 428 | Missing Idempotency-Key header |
| `RATE_LIMITED` | 429 | Bucket exhausted (+ Retry-After) |
| `INTERNAL_ERROR` | 500 | Unexpected failure (requestId for tracing) |
