# 抢票系统后端

![Java](https://img.shields.io/badge/Java-11-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[English](README.md)

面向千～万级并发场景的抢票系统后端，支持演出管理、选座购票、Redis 库存管理、订单超时自动取消、Mock 支付、入场核验及部分退款。采用 Maven 多模块架构，各模块独立部署。

---

## 功能特性

- **演出管理**：演出 / 场次 / 座位 CRUD；管理端一键预热座位库存到 Redis
- **场地模板**：在 Room 上一次性定义座位布局和默认价格；创建场次时传入 `roomId`，座位和价格区域自动复制
- **抢票核心**：Lua 原子限购检查 + Redis 批量锁座（任一失败全量回滚）+ 同步建单
- **防超卖**：Redis Set 原子扣库存，DB 层二次校验兜底
- **订单超时**：RabbitMQ TTL + 死信队列，5 分钟精准触发取消并释放库存
- **异步事件**：支付成功后通过 RabbitMQ Fanout 并行触发票券生成、DB 库存同步、通知（预留）
- **退款**：支持整单退款与单票退款；已部分退款订单（状态 5）可继续退剩余未使用票
- **注解限流**：`@RateLimit` 注解支持全局 / 用户 / IP 三维度固定窗口限流 + 黑名单拦截
- **参数校验**：`@Valid` + 全局异常处理，统一返回友好错误信息
- **入场核验**：支持二维码 / 票号双通道核销
- **JWT 认证**：`@NoLogin` 注解标记免登录接口，其余默认鉴权

---

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.x / JDK 11 |
| ORM | MyBatis | 3.5.x |
| 缓存 / 分布式锁 | Redis + Redisson | 7.x / 3.x |
| 消息队列 | RabbitMQ | 3.x |
| 数据库 | MySQL | 8.x |
| 鉴权 | Spring Security + JJWT | 0.12.x |
| 构建 | Maven | — |

---

## 模块结构

```
maill-backend/
├── common/      # 通用工具：响应封装、异常、雪花ID、RedisKeys、@RateLimit 注解 + AOP、黑名单
├── core/        # 核心业务：实体、Mapper、Service、MQ Producer/Consumer
├── admin/       # 管理端 REST API（端口 8081）
├── user/        # 用户端 REST API（端口 8082）
├── payment/     # 支付模块（端口 8083，预留）
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

启动 MySQL 8（3306）、Redis 7（6379）、RabbitMQ 3（5672，管理界面 15672）。`sql/schema.sql` 首次运行自动执行。

> **RabbitMQ 管理界面**：http://localhost:15672（guest / guest）

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
```

默认使用 `dev` profile，数据库密码为 `root123`，可在各模块 `application-dev.yml` 中修改。

### 4. 生成压测数据（可选）

```bash
# 依赖 jq：brew install jq
bash docs/seed-data.sh
```

自动创建 1 个场地模板（20×20 座位，前 10 行 VIP 区）、5 个演出、15 个场次，并全部发布和预热到 Redis。共 6 000 个可售座位，可直接用于压测。

---

## API 概览

### 用户端（:8082）

#### 认证

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/auth/register` | 注册 | ✗ |
| POST | `/api/auth/login` | 登录，返回 JWT | ✗ |

#### 演出

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/show/list` | 演出列表（分页，支持 name / category / venue 筛选） | ✗ |
| GET  | `/api/show/{id}` | 演出详情 | ✗ |

#### 场次

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/session/list` | 场次列表（分页，支持 status / startTime / endTime 筛选） | ✗ |
| POST | `/api/session/detail` | 场次座位图（含区域价格 + 实时可售状态） | ✗ |

#### 订单

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/order/submit` | 锁座 + 建单，直接返回完整订单 | ✓ |
| POST | `/api/order/cancel` | 取消订单（未支付直接取消；已支付 / 部分退款则发起退款） | ✓ |
| GET  | `/api/order/orderDetails` | 订单详情（仅限本人） | ✓ |
| POST | `/api/order/refundTicket` | 单票退款（支持已支付 / 部分退款订单） | ✓ |
| POST | `/api/order/list` | 我的订单（分页，支持 status / 日期范围筛选） | ✓ |

#### 支付 & 核验

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/payment/create` | 支付订单 | ✓ |
| POST | `/api/verify/qr` | 二维码核验入场 | ✗ |
| POST | `/api/verify/ticket` | 票号核验入场 | ✗ |

---

### 管理端（:8081）

#### 场地模板（Room 管理）

在 Room 上一次性定义座位布局和默认价格；创建场次时指定 `roomId`，座位和价格区域自动复制。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/room/create` | 创建场地 |
| PUT  | `/api/admin/room/update` | 更新场地 |
| GET  | `/api/admin/room/{id}` | 场地详情 |
| GET  | `/api/admin/room/list` | 场地列表 |
| POST | `/api/admin/room/seat/batch` | 保存场地座位模板（覆盖写） |
| GET  | `/api/admin/room/seat/list?roomId=` | 场地座位模板列表 |
| POST | `/api/admin/room/area/save` | 保存场地默认价格区域（覆盖写） |
| GET  | `/api/admin/room/area/list?roomId=` | 场地默认价格区域列表 |

#### 演出 & 场次

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/show/create` | 创建演出 |
| PUT  | `/api/admin/show/update` | 更新演出 |
| GET  | `/api/admin/show/{id}` | 演出详情 |
| GET  | `/api/admin/show/list` | 演出列表 |
| POST | `/api/admin/session/create` | 创建场次（传入 `roomId` 自动复制座位 + 价格） |
| PUT  | `/api/admin/session/update` | 更新场次 |
| GET  | `/api/admin/session/{id}` | 场次详情 |
| GET  | `/api/admin/session/list?showId=` | 场次列表 |
| PUT  | `/api/admin/session/{sessionId}/publish` | 发布场次开售 |

#### 座位（手动创建——无场地模板时使用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/seat/batch` | 批量创建座位 |
| GET  | `/api/admin/seat/list?sessionId=` | 座位列表 |
| POST | `/api/admin/seat/area/save` | 保存场次价格区域 |
| GET  | `/api/admin/seat/area/list?sessionId=` | 场次价格区域列表 |
| POST | `/api/admin/seat/warmup/{sessionId}` | 预热座位库存到 Redis |

#### 订单 & 监控

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/admin/order/{id}` | 订单详情 |
| GET  | `/api/admin/order/query` | 订单查询 |
| GET  | `/api/admin/order/{id}/items` | 订单明细 |
| GET  | `/api/admin/monitor/dashboard?sessionId=` | 实时座位统计（总数 / 可售 / 已售） |

---

## 核心购票流程

```
用户选座后提交
    │
    ├─ @RateLimit 黑名单 / IP / 用户 / 全局限流（AOP，最先拦截）
    ├─ 场次校验
    ├─ Lua 原子限购检查（Redis）
    ├─ Lua 批量锁座（任一失败全量回滚）
    ├─ 同步建单（DB INSERT）
    ├─ 发送超时消息到 RabbitMQ（TTL = 5 分钟）
    └─ 直接返回完整订单信息（含演出 / 场次 / 座位 / 总价 / 倒计时）
                │
    ┌───────────┴───────────┐
    │                       │
用户在确认页点击支付      5 分钟内未支付
    │                       │
POST /api/payment/create  超时消息经死信路由
    │                  至 order.cancel.queue
    ├─ 创建支付记录         │
    ├─ 订单状态 → 已支付    取消订单
    └─ 发送支付成功事件     释放 Redis 库存
              │             回滚限购计数
    ┌─────────┼──────────┐
    │         │          │
生成票券   同步DB库存   发送通知
（异步）   （异步）    （预留）
```

---

## 退款流程

```
订单状态 1（已支付）或 5（部分退款）
    │
    ├─ 整单取消 POST /api/order/cancel
    │       └─ 查询所有未使用票 → doRefund → 状态 → 退款中(3)
    │
    └─ 单票退款 POST /api/order/refundTicket
            └─ 校验票状态未使用 → doRefund → 状态 → 退款中(3)
                        │
              MQ 消费者处理退款结果
                        │
            ┌───────────┴───────────┐
            │                       │
      仍有未退票             所有票已退
   状态 → 部分退款(5)      状态 → 已退款(4)
```

---

## 订单状态说明

| 状态值 | 含义 |
|:------:|------|
| 0 | 待支付 |
| 1 | 已支付 |
| 2 | 已取消 |
| 3 | 退款中 |
| 4 | 已退款 |
| 5 | 部分退款 |

---

## 消息队列设计

```
订单超时（TTL + 死信）：
  order.timeout.exchange ──→ order.timeout.queue（TTL 5分钟）
                                      │ 到期
  order.dead.exchange    ──→ order.cancel.queue ──→ OrderTimeoutConsumer（取消订单）

支付成功（Fanout）：
  payment.success.exchange ──→ ticket.generate.queue  ──→ 生成票券
                           ──→ inventory.sync.queue   ──→ 同步 seat.status = 已售
                           ──→ notification.queue     ──→ 通知（预留）
```

---

## 注解限流

在任意 Controller 方法上叠加 `@RateLimit` 注解，AOP 自动拦截，无需侵入业务代码：

```java
@RateLimit(type = LimitType.BLACKLIST)
@RateLimit(type = LimitType.IP,     limit = 20,  window = 60)
@RateLimit(type = LimitType.USER,   limit = 5,   window = 60)
@RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙")
@PostMapping("/submit")
public Result<?> submit(...) { }
```

**检查顺序**：黑名单 → IP 限流 → 用户限流 → 全局限流，越早拦截越轻量。

---

## 数据库设计

共 13 张表：

| 表名 | 说明 |
|------|------|
| `user` | 用户，BCrypt 密码 |
| `user_role` | 用户角色（USER / ADMIN） |
| `show` | 演出 |
| `show_session` | 场次；`room_id` 关联场地模板；含限购数 `limit_per_user` |
| `seat` | 座位底表，实时库存由 Redis 管理，支付后异步同步 status |
| `seat_area` | 场次座位价格区域 |
| `order` | 订单，索引 `idx_status_expire` |
| `order_item` | 订单行，含价格快照 |
| `payment` | 支付记录 |
| `ticket` | 票券，8 位友好票号（排除 O/0/I/1）+ UUID 二维码 |
| `room` | 场地模板（名称、行列数等） |
| `room_seat` | 场地座位布局模板 |
| `room_area` | 场地默认价格区域（创建场次时复制到 `seat_area`） |

---

## Redis 设计

| Key | 类型 | 说明 | TTL |
|-----|------|------|-----|
| `session:seats:{sessionId}` | Set | 可售座位 ID 集合 | 7 天 |
| `seat:info:{seatId}` | Hash | 座位详情（行 / 列 / 类型 / 区域） | 7 天 |
| `seat:lock:{sessionId}:{seatId}` | String | 座位锁（value = userId） | 5 分钟 |
| `session:purchase:{sessionId}:{userId}` | String | 用户已购数量 | 7 天 |
| `session:area:price:{sessionId}:{areaId}` | Hash | 区域价格缓存 | 7 天 |
| `session:locked:{sessionId}` | String | 当前正在结算中（已锁座未支付）的座位数量 | 7 天 |
| `rate:global:{method}:{window}` | String | 全局限流计数 | 动态 |
| `rate:user:{userId}:{method}:{window}` | String | 用户限流计数 | 动态 |
| `rate:ip:{ip}:{method}:{window}` | String | IP 限流计数 | 动态 |
| `blacklist:user:{userId}` | String | 用户黑名单 | 自定义 |
| `blacklist:ip:{ip}` | String | IP 黑名单 | 自定义 |

---

## 高并发设计要点

| 问题 | 方案 |
|------|------|
| 超卖 | Redis Set `SREM` 原子扣库存 + DB 层二次校验兜底 |
| 限购 | Lua 脚本原子 INCR + 阈值检查 |
| 流量削峰 | `@RateLimit` 注解限流，全局 / 用户 / IP 三维度 |
| 批量锁座 | Lua 脚本，任一失败全量回滚，不留半锁 |
| 订单超时 | RabbitMQ TTL + 死信队列，精准 5 分钟触发 |
| 支付后解耦 | RabbitMQ Fanout，票券 / 库存 / 通知并行异步处理 |
| 黑名单 | Redis key 存储，AOP 最先检查，不消耗限流计数 |

---

## 安全

- 密码：BCrypt 存储
- 认证：JWT（30 分钟过期），`@NoLogin` 注解标记公开接口
- 越权防护：订单接口校验 `order.userId == 当前登录用户`
- 防注入：MyBatis `#{}` 参数化查询
- 金额校验：后端重新计算总价，不信任前端传值
- 参数校验：`@Valid` + `GlobalExceptionHandler` 统一处理

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
         │    管理端 API    │      │    用户端 API    │
         └────────┬────────┘      └────────┬────────┘
                  │                         │
                  └────────────┬────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            │                  │                  │
   ┌────────▼────────┐ ┌───────▼──────┐ ┌────────▼────────┐
   │    MySQL 8      │ │   Redis 7    │ │   RabbitMQ 3    │
   │   (主从可选)     │ │  (缓存/锁)   │ │  (事件 / 超时)  │
   └─────────────────┘ └──────────────┘ └─────────────────┘
```

---

## 扩展方向

- **真实支付**：实现 `PaymentGateway` 接口对接支付宝 / 微信
- **通知服务**：接入短信 / 推送，实现 `notification.queue` 消费者
- **微服务化**：admin / user / payment 拆分独立部署 + API Gateway
- **分库分表**：订单表按 `session_id` 分片
- **CDN**：演出海报等静态资源加速

---

## License

[MIT](LICENSE)
