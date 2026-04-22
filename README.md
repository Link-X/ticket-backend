# 抢票系统后端

![Java](https://img.shields.io/badge/Java-11-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

面向千~万级并发场景的抢票系统后端，支持演出管理、选座购票、Redis 库存管理、订单超时取消、Mock 支付与入场核验。采用 Maven 多模块架构，各模块独立部署。

---

## 功能特性

- **演出管理**：演出/场次/座位的 CRUD，管理端预热座位库存到 Redis
- **抢票核心**：Lua 脚本原子限购检查 + Redis Stream 削峰 + 异步消费者创建订单
- **防超卖**：Redis Set 原子扣库存，DB 层兜底校验
- **订单超时**：5 分钟未支付自动取消，定时任务 30s 扫描兜底
- **支付**：可扩展网关接口，内置 Mock 实现，支付成功后生成票券
- **入场核验**：支持二维码 / 票号双通道核销
- **JWT 认证**：`@NoLogin` 注解标记免登录接口，其余默认鉴权

---

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.x / JDK 11 |
| ORM | MyBatis | 3.5.x |
| 缓存 / 分布式锁 | Redis + Redisson | 7.x / 3.x |
| 消息队列 | Redis Stream | — |
| 数据库 | MySQL | 8.x |
| 鉴权 | Spring Security + JJWT | 0.12.x |
| 构建 | Maven | — |

---

## 模块结构

```
maill-backend/
├── common/      # 通用工具：响应封装、异常处理、雪花ID、RedisKeys
├── core/        # 核心业务：实体、Mapper、Service、消费者
├── admin/       # 管理端 REST API（端口 8081）
├── user/        # 用户端 REST API（端口 8082）
├── payment/     # 支付模块（端口 8083）
├── sql/
│   └── schema.sql
└── docker-compose.yml
```

依赖关系：

```
common ← core ← admin
                 user
                 payment
```

---

## 快速启动

### 前置要求

- JDK 11+
- Maven 3.8+
- Docker & Docker Compose

### 1. 启动基础服务

```bash
docker-compose up -d
```

启动 MySQL 8（3306）和 Redis 7（6379），并自动执行 `sql/schema.sql` 建表。

### 2. 编译

```bash
mvn compile -q
```

### 3. 启动各模块

```bash
# 管理端（8081）
mvn spring-boot:run -pl admin

# 用户端（8082）
mvn spring-boot:run -pl user

# 支付（8083）
mvn spring-boot:run -pl payment
```

默认使用 `dev` profile，数据库密码为 `root123`，可在各模块 `application-dev.yml` 中修改。

---

## API 概览

### 用户端（:8082）

| 方法 | 路径 | 说明 | 是否需要登录 |
|------|------|------|:---:|
| POST | `/api/auth/register` | 注册 | ✗ |
| POST | `/api/auth/login` | 登录，返回 JWT | ✗ |
| GET  | `/api/show/list` | 上架演出列表 | ✗ |
| GET  | `/api/show/{id}/sessions` | 场次列表 | ✗ |
| GET  | `/api/show/session/{id}/seats` | 可售座位（Redis） | ✗ |
| POST | `/api/order/submit` | 提交购票，写入 Redis Stream | ✓ |
| POST | `/api/payment/create` | 支付订单 | ✓ |
| GET  | `/api/verify/qr/{qrCode}` | 二维码核验 | ✗ |
| GET  | `/api/verify/ticket/{ticketNo}` | 票号核验 | ✗ |

### 管理端（:8081）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/show/create` | 创建演出 |
| POST | `/api/admin/session/create` | 创建场次 |
| POST | `/api/admin/seat/batch` | 批量创建座位 |
| POST | `/api/admin/seat/warmup/{sessionId}` | 预热座位库存到 Redis |
| GET  | `/api/admin/order/{id}` | 查询订单 |
| GET  | `/api/admin/monitor/dashboard` | 实时可售座位数 |

完整的端到端调用示例见 [`docs/e2e-verify.http`](docs/e2e-verify.http)（IntelliJ / VS Code REST Client 格式）。

---

## 数据库设计

共 9 张表：

| 表名 | 说明 |
|------|------|
| `user` | 用户，BCrypt 密码 |
| `user_role` | 用户角色（USER / ADMIN） |
| `show` | 演出 |
| `show_session` | 场次，含限购数 `limit_per_user` |
| `seat` | 座位底表，仅做持久记录，实时库存由 Redis 管理 |
| `order` | 订单，索引 `idx_status_expire` 供超时扫描 |
| `order_item` | 订单行，含价格快照 |
| `payment` | 支付记录 |
| `ticket` | 票券，含 8 位友好票号（排除 O/0/I/1）和 UUID 二维码 |

> **库存权威**：`session:seats:{sessionId}`（Redis Set）是唯一库存来源。`seat.status` 仅在支付成功后异步更新，用于历史追溯，不参与实时库存判断。

---

## Redis 设计

| Key | 类型 | 说明 | TTL |
|-----|------|------|-----|
| `session:seats:{sessionId}` | Set | 可售座位 ID 集合 | 7 天 |
| `seat:info:{seatId}` | Hash | 座位详情（行/列/类型/价格） | 7 天 |
| `seat:lock:{sessionId}:{seatId}` | String | 座位锁（value = userId） | 5 分钟 |
| `session:purchase:{sessionId}:{userId}` | String | 用户已购数量 | 7 天 |
| `ticket:order:stream` | Stream | 购票请求队列 | — |

关键操作均通过 **Lua 脚本**保证原子性：限购检查（INCR + 边界检查）、批量锁座（SETNX 失败全量回滚）、释放座位（DEL lock + SADD 回集合）。

---

## 部署架构

```
                      ┌──────────────────┐
                      │      Nginx       │
                      │  (反向代理/SSL)   │
                      └────────┬─────────┘
                               │
                  ┌────────────┴────────────┐
                  │                         │
         ┌────────▼────────┐      ┌────────▼────────┐
         │  admin : 8081   │      │  user  : 8082   │
         │   Spring Boot   │      │   Spring Boot   │
         │    管理端 API    │      │    用户端 API    │
         └────────┬────────┘      └────────┬────────┘
                  │                         │
                  └────────────┬────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            │                  │                  │
   ┌────────▼────────┐ ┌───────▼──────┐ ┌────────▼────────┐
   │    MySQL 8      │ │   Redis 7    │ │  Redis Stream   │
   │   (主从可选)     │ │  (缓存/锁)   │ │   (消息队列)    │
   └─────────────────┘ └──────────────┘ └─────────────────┘
```

- **单机**：所有组件同一台机器，docker-compose 一键启动
- **扩展**：Nginx 负载多个 user 实例；MySQL 主从；Redis 哨兵/Sentinel

---

## 核心购票流程

```
用户提交购票
    │
    ├─ Lua 限购检查（Redis）
    │
    ├─ 写入 Redis Stream
    │
    └─ 返回 requestId + "QUEUED"
                │
        TicketOrderConsumer 消费
                │
        ├─ Lua 批量锁座（任一失败全量回滚）
        ├─ DB 超卖兜底校验
        ├─ 创建 Order（status=0，5 分钟过期）
        └─ 创建 OrderItem
                │
        ┌───────┴───────┐
        │               │
    用户支付         5 分钟超时
        │               │
    status=1        定时任务取消
    生成 Ticket     释放 Redis 库存
```

---

## 高并发设计要点

| 问题 | 方案 |
|------|------|
| 超卖 | Redis Set `SREM` 原子扣库存 + DB 层兜底 |
| 限购 | Lua 脚本原子 INCR + 阈值检查 |
| 削峰 | Redis Stream 队列 + 后台消费者 |
| 锁粒度 | 单座直接锁 `seat:lock`；连座 Lua 批量操作，任一失败全回滚 |
| 超时释放 | 定时任务 30s 扫描 `idx_status_expire` 索引，兜底释放 |

---

## 安全

- 密码：BCrypt 存储
- 认证：JWT（30 分钟过期），`@NoLogin` 注解标记公开接口
- 防注入：MyBatis `#{}` 参数化查询
- 金额校验：后端重新计算总价，不信任前端传值

---

## 扩展方向

- **真实支付**：实现 `PaymentGateway` 接口对接支付宝 / 微信
- **微服务化**：admin / user / payment 拆分独立部署 + API Gateway
- **更大规模 MQ**：Redis Stream → RocketMQ / RabbitMQ
- **分库分表**：订单表按 `session_id` 分片
- **CDN**：演出海报等静态资源加速

---

## 压测目标

| 指标 | 目标值 |
|------|--------|
| 查询接口 P99 | < 500ms |
| 下单 P99（MQ 削峰后） | < 2s |
| 座位锁定 QPS（Redis 层） | 1000+ |
| 单机并发用户 | 5000+ |
| 万座预热耗时 | < 3s |

---

## License

MIT
