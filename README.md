# Ticket Booking System — Backend

![Java](https://img.shields.io/badge/Java-11-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[中文文档](README.zh.md)

A high-concurrency ticket booking backend supporting thousands to tens of thousands of concurrent users. Features show management, seat selection, Redis-based inventory, order timeout cancellation, mock payment, and venue check-in verification. Built with a Maven multi-module architecture for independent deployment.

---

## Features

- **Show Management** — CRUD for shows / sessions / seats; admin warms up seat inventory into Redis
- **Ticket Booking Core** — Lua atomic purchase-limit check + Redis Stream queue buffering + async order consumer
- **Oversell Prevention** — Redis Set atomic deduction + DB-level safety check
- **Order Timeout** — Auto-cancel after 5 minutes; scheduled job scans every 30s as a fallback
- **Payment** — Extensible `PaymentGateway` interface with built-in Mock implementation; generates tickets on success
- **Check-in Verification** — Dual-channel: QR code or ticket number
- **JWT Auth** — `@NoLogin` annotation marks public endpoints; all others require authentication by default

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 2.7.x / JDK 11 |
| ORM | MyBatis | 3.5.x |
| Cache / Lock | Redis + Redisson | 7.x / 3.x |
| Message Queue | Redis Stream | — |
| Database | MySQL | 8.x |
| Auth | Spring Security + JJWT | 0.12.x |
| Build | Maven | — |

---

## Module Structure

```
maill-backend/
├── common/      # Utilities: response wrapper, exceptions, Snowflake ID, RedisKeys
├── core/        # Core business: entities, Mappers, Services, consumer
├── admin/       # Admin REST API  (port 8081)
├── user/        # User  REST API  (port 8082)
├── payment/     # Payment module  (port 8083)
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

Starts MySQL 8 (port 3306) and Redis 7 (port 6379). `sql/schema.sql` is executed automatically on first run.

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

# Payment service (8083)
mvn spring-boot:run -pl payment
```

The default profile is `dev`. Database password is `root123`. Edit each module's `application-dev.yml` to change.

---

## API Overview

### User Service (:8082)

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/auth/register` | Register | ✗ |
| POST | `/api/auth/login` | Login, returns JWT | ✗ |
| GET  | `/api/show/list` | List published shows | ✗ |
| GET  | `/api/show/{id}/sessions` | List sessions | ✗ |
| GET  | `/api/show/session/{id}/seats` | Available seats (from Redis) | ✗ |
| POST | `/api/order/submit` | Submit booking, enqueues to Redis Stream | ✓ |
| POST | `/api/payment/create` | Pay order | ✓ |
| GET  | `/api/verify/qr/{qrCode}` | Verify by QR code | ✗ |
| GET  | `/api/verify/ticket/{ticketNo}` | Verify by ticket number | ✗ |

### Admin Service (:8081)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/show/create` | Create show |
| POST | `/api/admin/session/create` | Create session |
| POST | `/api/admin/seat/batch` | Batch create seats |
| POST | `/api/admin/seat/warmup/{sessionId}` | Warm up seat inventory into Redis |
| GET  | `/api/admin/order/{id}` | Get order |
| GET  | `/api/admin/monitor/dashboard` | Real-time available seat count |

For a full end-to-end walkthrough see [`docs/e2e-verify.http`](docs/e2e-verify.http) (IntelliJ / VS Code REST Client format).

---

## Database Design

9 tables in total:

| Table | Description |
|-------|-------------|
| `user` | Users, BCrypt passwords |
| `user_role` | Roles: USER / ADMIN |
| `show` | Shows |
| `show_session` | Sessions, includes `limit_per_user` |
| `seat` | Seat master table — persisted record only; real-time inventory lives in Redis |
| `order` | Orders; index `idx_status_expire` for timeout scans |
| `order_item` | Order lines with price snapshot |
| `payment` | Payment records |
| `ticket` | Tickets with 8-char friendly ticket number (alphanumeric, excludes O/0/I/1) and UUID QR code |

> **Inventory source of truth**: the Redis Set `session:seats:{sessionId}` is the only authoritative inventory. `seat.status` is updated asynchronously after payment for historical records only — it does not participate in real-time inventory decisions.

---

## Redis Design

| Key | Type | Description | TTL |
|-----|------|-------------|-----|
| `session:seats:{sessionId}` | Set | Available seat ID pool | 7 days |
| `seat:info:{seatId}` | Hash | Seat details: row / col / type / price | 7 days |
| `seat:lock:{sessionId}:{seatId}` | String | Seat lock (value = userId) | 5 min |
| `session:purchase:{sessionId}:{userId}` | String | Per-user purchase count | 7 days |
| `ticket:order:stream` | Stream | Booking request queue | — |

All critical operations use **Lua scripts** for atomicity: purchase-limit check (GET + INCR), batch seat locking (SETNX with full rollback on any failure), seat release (DEL lock + SADD back to pool).

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
         │   Spring Boot   │      │   Spring Boot   │
         │   Admin API     │      │    User API     │
         └────────┬────────┘      └────────┬────────┘
                  │                         │
                  └────────────┬────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            │                  │                  │
   ┌────────▼────────┐ ┌───────▼──────┐ ┌────────▼────────┐
   │    MySQL 8      │ │   Redis 7    │ │  Redis Stream   │
   │  (replication   │ │ (cache/lock) │ │ (message queue) │
   │   optional)     │ │              │ │                 │
   └─────────────────┘ └──────────────┘ └─────────────────┘
```

- **Single machine** — all components on one host, started via docker-compose
- **Scaled out** — Nginx load-balances multiple user instances; MySQL replication; Redis Sentinel

---

## Core Booking Flow

```
User submits booking
    │
    ├─ Lua purchase-limit check (Redis)
    │
    ├─ Enqueue to Redis Stream
    │
    └─ Return requestId + "QUEUED"
                │
        TicketOrderConsumer picks up message
                │
        ├─ Lua batch seat lock (full rollback on any failure)
        ├─ DB oversell safety check
        ├─ Create Order (status=0, expires in 5 min)
        └─ Create OrderItems
                │
        ┌───────┴────────┐
        │                │
    User pays        Timeout (5 min)
        │                │
    status=1         Scheduled job cancels
    Generate         Releases Redis inventory
    Tickets
```

---

## Concurrency Design

| Problem | Solution |
|---------|----------|
| Oversell | Redis Set `SREM` atomic deduction + DB safety check |
| Purchase limit | Lua atomic INCR + threshold check |
| Traffic spike | Redis Stream queue + async consumer |
| Lock granularity | Single seat: direct `seat:lock`; multi-seat: Lua batch with full rollback |
| Timeout release | Scheduled job scans `idx_status_expire` index every 30s |

---

## Security

- Passwords stored with BCrypt
- JWT authentication (30-minute expiry); `@NoLogin` marks public endpoints
- SQL injection prevention via MyBatis `#{}` parameterized queries
- Server-side price recalculation — client-supplied amounts are never trusted

---

## Roadmap

- **Real payment gateways** — implement `PaymentGateway` for Alipay / WeChat Pay
- **Microservices** — split admin / user / payment into independent services behind an API Gateway
- **Larger-scale MQ** — replace Redis Stream with RocketMQ / RabbitMQ at higher scale
- **Sharding** — partition the order table by `session_id`
- **CDN** — offload show poster and static assets

---

## Performance Targets

| Metric | Target |
|--------|--------|
| Query P99 latency | < 500 ms |
| Order submit P99 (after queue buffering) | < 2 s |
| Seat lock QPS (Redis layer) | 1,000+ |
| Concurrent users (single node) | 5,000+ |
| Warm-up time (10,000 seats) | < 3 s |

---

## License

[MIT](LICENSE)
