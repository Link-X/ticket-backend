# Ticket Booking System — Backend

![Java](https://img.shields.io/badge/Java-11-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[中文文档](README.zh.md)

A high-concurrency ticket booking backend targeting thousands to tens of thousands of concurrent users. Features show management, seat selection, Redis-based inventory, order timeout cancellation, mock payment, venue check-in verification, and partial refunds. Built with a Maven multi-module architecture for independent deployment.

---

## Features

- **Show Management** — CRUD for shows / sessions / seats; admin warms up seat inventory into Redis with one click
- **Venue Templates** — Define seat layout and default prices once on a room; sessions created with a `roomId` auto-copy all seats and price areas instantly
- **Booking Core** — Lua atomic purchase-limit check + Redis batch seat lock (full rollback on any failure) + synchronous order creation
- **Oversell Prevention** — Redis Set atomic `SREM` deduction + DB-level safety check
- **Order Timeout** — RabbitMQ TTL + dead-letter queue, cancels order and releases inventory exactly 5 minutes after creation
- **Async Events** — After payment, RabbitMQ Fanout fan-out triggers ticket generation, DB inventory sync, and notification (reserved) in parallel
- **Refunds** — Full-order and per-ticket refunds; partially-refunded orders (status 5) can continue to refund remaining unused tickets
- **Annotation Rate Limiting** — `@RateLimit` annotation supports GLOBAL / USER / IP three-dimensional fixed-window rate limiting + blacklist interception
- **Parameter Validation** — `@Valid` + global exception handler returns unified friendly error messages
- **Check-in Verification** — Dual-channel: QR code or ticket number
- **JWT Auth** — `@NoLogin` annotation marks public endpoints; all others require authentication by default

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 2.7.x / JDK 11 |
| ORM | MyBatis | 3.5.x |
| Cache / Lock | Redis + Redisson | 7.x / 3.x |
| Message Queue | RabbitMQ | 3.x |
| Database | MySQL | 8.x |
| Auth | Spring Security + JJWT | 0.12.x |
| Build | Maven | — |

---

## Module Structure

```
maill-backend/
├── common/      # Utilities: response wrapper, exceptions, Snowflake ID, RedisKeys, @RateLimit annotation + AOP, blacklist
├── core/        # Core business: entities, Mappers, Services, MQ Producer/Consumer
├── admin/       # Admin REST API  (port 8081)
├── user/        # User  REST API  (port 8082)
├── payment/     # Payment module  (port 8083, reserved)
├── sql/
│   └── schema.sql
└── docker-compose.yml
```

Dependency chain:

```
common ← core ← admin
                 user
                 payment
```

---

## Quick Start

### Prerequisites

- JDK 11+
- Maven 3.8+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker-compose up -d
```

Starts MySQL 8 (3306), Redis 7 (6379), and RabbitMQ 3 (5672, management UI on 15672). `sql/schema.sql` is executed automatically on first run.

> **RabbitMQ Management UI**: http://localhost:15672 (guest / guest)

### 2. Build

```bash
mvn compile -q
```

### 3. Run modules

```bash
# Admin service (8081)
mvn spring-boot:run -pl admin

# User service (8082)
mvn spring-boot:run -pl user
```

The default profile is `dev`. Database password is `root123`. Edit each module's `application-dev.yml` to change.

### 4. Seed load-test data (optional)

```bash
# Requires jq: brew install jq
bash docs/seed-data.sh
```

Creates 1 venue template (20 × 20 seats, VIP front section), 5 shows, 15 sessions, then publishes and warms up all sessions into Redis. Total: 6 000 bookable seats ready for load testing.

---

## API Overview

### User Service (:8082)

#### Auth

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/auth/register` | Register | ✗ |
| POST | `/api/auth/login` | Login, returns JWT | ✗ |

#### Shows

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/show/list` | Published show list (paginated, filterable by name / category / venue) | ✗ |
| GET  | `/api/show/{id}` | Show detail | ✗ |

#### Sessions

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/session/list` | Session list (paginated, filterable by status / startTime / endTime) | ✗ |
| POST | `/api/session/detail` | Session seat map (area prices + real-time availability) | ✗ |

#### Orders

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/order/submit` | Lock seats + create order, returns full order immediately | ✓ |
| POST | `/api/order/cancel` | Cancel order (unpaid: sync cancel; paid / partial-refund: initiate refund) | ✓ |
| GET  | `/api/order/orderDetails` | Order detail (owner only) | ✓ |
| POST | `/api/order/refundTicket` | Refund a single ticket (works on paid or partially-refunded orders) | ✓ |
| POST | `/api/order/list` | My orders (paginated, filterable by status / date range) | ✓ |

#### Payment & Verification

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/payment/create` | Pay order | ✓ |
| POST | `/api/verify/qr` | Verify by QR code | ✗ |
| POST | `/api/verify/ticket` | Verify by ticket number | ✗ |

---

### Admin Service (:8081)

#### Venue Templates (Room Management)

Define the seat layout and default prices on a room once; specifying `roomId` when creating a session automatically copies all seats and price areas.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/room/create` | Create room |
| PUT  | `/api/admin/room/update` | Update room |
| GET  | `/api/admin/room/{id}` | Room detail |
| GET  | `/api/admin/room/list` | Room list |
| POST | `/api/admin/room/seat/batch` | Save room seat template (overwrite) |
| GET  | `/api/admin/room/seat/list?roomId=` | Room seat template list |
| POST | `/api/admin/room/area/save` | Save room default price areas (overwrite) |
| GET  | `/api/admin/room/area/list?roomId=` | Room default price area list |

#### Shows & Sessions

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/show/create` | Create show |
| PUT  | `/api/admin/show/update` | Update show |
| GET  | `/api/admin/show/{id}` | Show detail |
| GET  | `/api/admin/show/list` | Show list |
| POST | `/api/admin/session/create` | Create session (pass `roomId` to auto-copy seats + prices) |
| PUT  | `/api/admin/session/update` | Update session |
| GET  | `/api/admin/session/{id}` | Session detail |
| GET  | `/api/admin/session/list?showId=` | Session list |
| PUT  | `/api/admin/session/{sessionId}/publish` | Publish session for sale |

#### Seats (Manual — use when no room template)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/seat/batch` | Batch create seats |
| GET  | `/api/admin/seat/list?sessionId=` | Seat list |
| POST | `/api/admin/seat/area/save` | Save session price areas |
| GET  | `/api/admin/seat/area/list?sessionId=` | Session price area list |
| POST | `/api/admin/seat/warmup/{sessionId}` | Warm up seat inventory into Redis |

#### Orders & Monitor

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/api/admin/order/{id}` | Order detail |
| GET  | `/api/admin/order/query` | Order query |
| GET  | `/api/admin/order/{id}/items` | Order line items |
| GET  | `/api/admin/monitor/dashboard?sessionId=` | Real-time seat counts (total / available / sold) |

---

## Core Booking Flow

```
User submits seat selection
    │
    ├─ @RateLimit blacklist / IP / user / global check (AOP, first to intercept)
    ├─ Session validation
    ├─ Lua atomic purchase-limit check (Redis)
    ├─ Lua batch seat lock (full rollback on any failure)
    ├─ Synchronous order creation (DB INSERT)
    ├─ Send timeout message to RabbitMQ (TTL = 5 min)
    └─ Return full order info (show / session / seats / total / countdown)
                │
    ┌───────────┴───────────┐
    │                       │
User clicks Pay on       No payment within 5 min
  confirmation page          │
    │                   Timeout message routed via DLX
POST /api/payment/create to order.cancel.queue
    │                       │
    ├─ Create payment record Cancel order
    ├─ Order status → PAID  Release Redis inventory
    └─ Send payment event   Roll back purchase count
              │
    ┌─────────┼──────────┐
    │         │          │
Generate   Sync DB    Send notification
Tickets   inventory   (reserved)
(async)    (async)
```

---

## Refund Flow

```
Order status: 1 (PAID) or 5 (PARTIAL_REFUND)
    │
    ├─ Full cancel  POST /api/order/cancel
    │       └─ Find all unused tickets → doRefund → status → REFUNDING (3)
    │
    └─ Per-ticket   POST /api/order/refundTicket
            └─ Validate ticket is unused → doRefund → status → REFUNDING (3)
                        │
              MQ consumer processes refund result
                        │
            ┌───────────┴───────────┐
            │                       │
    Unused tickets remain       All tickets refunded
    status → PARTIAL_REFUND (5) status → REFUNDED (4)
```

---

## Order Status Reference

| Status | Meaning |
|:------:|---------|
| 0 | Pending payment |
| 1 | Paid |
| 2 | Cancelled |
| 3 | Refunding |
| 4 | Refunded |
| 5 | Partially refunded |

---

## Message Queue Design

```
Order Timeout (TTL + Dead Letter):
  order.timeout.exchange ──→ order.timeout.queue (TTL 5 min)
                                      │ expires
  order.dead.exchange    ──→ order.cancel.queue ──→ OrderTimeoutConsumer (cancel order)

Payment Success (Fanout):
  payment.success.exchange ──→ ticket.generate.queue  ──→ generate tickets
                           ──→ inventory.sync.queue   ──→ sync seat.status = SOLD
                           ──→ notification.queue     ──→ notify (reserved)
```

---

## Annotation Rate Limiting

Stack `@RateLimit` annotations on any Controller method — AOP intercepts automatically, no business code changes needed:

```java
@RateLimit(type = LimitType.BLACKLIST)
@RateLimit(type = LimitType.IP,     limit = 20,  window = 60)
@RateLimit(type = LimitType.USER,   limit = 5,   window = 60)
@RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "System busy")
@PostMapping("/submit")
public Result<?> submit(...) { }
```

**Check order**: Blacklist → IP → User → Global. Earlier checks are cheaper to evaluate.

---

## Database Design

13 tables in total:

| Table | Description |
|-------|-------------|
| `user` | Users, BCrypt passwords |
| `user_role` | Roles: USER / ADMIN |
| `show` | Shows |
| `show_session` | Sessions; `room_id` links the venue template; `limit_per_user` cap |
| `seat` | Seat master table; real-time inventory lives in Redis, `status` synced async after payment |
| `seat_area` | Per-session seat price areas |
| `order` | Orders; index `idx_status_expire` |
| `order_item` | Order lines with price snapshot |
| `payment` | Payment records |
| `ticket` | Tickets with 8-char friendly ticket number (excludes O/0/I/1) + UUID QR code |
| `room` | Venue template (name, dimensions) |
| `room_seat` | Seat layout template for a room |
| `room_area` | Default price areas for a room (copied to `seat_area` on session creation) |

---

## Redis Design

| Key | Type | Description | TTL |
|-----|------|-------------|-----|
| `session:seats:{sessionId}` | Set | Available seat ID pool | 7 days |
| `seat:info:{seatId}` | Hash | Seat details: row / col / type / area | 7 days |
| `seat:lock:{sessionId}:{seatId}` | String | Seat lock (value = userId) | 5 min |
| `session:purchase:{sessionId}:{userId}` | String | Per-user purchase count | 7 days |
| `session:area:price:{sessionId}:{areaId}` | Hash | Area price cache | 7 days |
| `session:locked:{sessionId}` | String | Count of seats currently locked in checkout | 7 days |
| `rate:global:{method}:{window}` | String | Global rate-limit counter | dynamic |
| `rate:user:{userId}:{method}:{window}` | String | User rate-limit counter | dynamic |
| `rate:ip:{ip}:{method}:{window}` | String | IP rate-limit counter | dynamic |
| `blacklist:user:{userId}` | String | User blacklist | custom |
| `blacklist:ip:{ip}` | String | IP blacklist | custom |

---

## Concurrency Design

| Problem | Solution |
|---------|----------|
| Oversell | Redis Set `SREM` atomic deduction + DB safety check |
| Purchase limit | Lua atomic INCR + threshold check |
| Traffic spike | `@RateLimit` annotation limiting: global / user / IP three dimensions |
| Batch seat lock | Lua script — full rollback on any failure, no partial locks |
| Order timeout | RabbitMQ TTL + dead-letter queue, exactly 5 minutes |
| Post-payment decoupling | RabbitMQ Fanout — ticket / inventory / notification processed async in parallel |
| Blacklist | Redis key storage, checked by AOP first, does not consume rate-limit counters |

---

## Security

- Passwords stored with BCrypt
- JWT authentication (30-minute expiry); `@NoLogin` marks public endpoints
- IDOR protection: order endpoints verify `order.userId == current user`
- SQL injection prevention via MyBatis `#{}` parameterized queries
- Server-side price recalculation — client-supplied amounts are never trusted
- Parameter validation: `@Valid` + `GlobalExceptionHandler` unified error handling

---

## Deployment Architecture

```
                      ┌──────────────────┐
                      │      Nginx       │
                      │  (Reverse Proxy) │
                      └────────┬─────────┘
                               │
                  ┌────────────┴────────────┐
                  │                         │
         ┌────────▼────────┐      ┌────────▼────────┐
         │  admin : 8081   │      │  user  : 8082   │
         │   Admin API     │      │    User API     │
         └────────┬────────┘      └────────┬────────┘
                  │                         │
                  └────────────┬────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            │                  │                  │
   ┌────────▼────────┐ ┌───────▼──────┐ ┌────────▼────────┐
   │    MySQL 8      │ │   Redis 7    │ │   RabbitMQ 3    │
   │  (replication   │ │ (cache/lock) │ │ (events/timeout)│
   │   optional)     │ │              │ │                 │
   └─────────────────┘ └──────────────┘ └─────────────────┘
```

---

## Roadmap

- **Real payment gateways** — implement `PaymentGateway` for Alipay / WeChat Pay
- **Notification service** — integrate SMS / push, implement `notification.queue` consumer
- **Microservices** — split admin / user / payment into independent services behind an API Gateway
- **Sharding** — partition the order table by `session_id`
- **CDN** — offload show poster and static assets

---

## License

[MIT](LICENSE)
