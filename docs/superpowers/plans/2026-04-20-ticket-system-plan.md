# 抢票系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 从零构建一个中等规模抢票系统后端,包含 Maven 多模块、用户认证、演出/场次/座位管理、抢票核心流程(Redis 库存/限流/削峰)、订单支付、入场核验。

**Architecture:** Spring Boot 2.7.x 多模块 Maven 项目(common/core/admin/user/payment),Redis 做库存/锁/限流/队列,MySQL 做持久化,MyBatis 做 ORM。

**Tech Stack:** Java 11, Spring Boot 2.7.x, MyBatis 3.5.x, Redis + Redisson, Redis Stream, MySQL 8, Spring Security + JWT, Maven

---

## 文件结构总览

```
maill-backend/
├── pom.xml                              # 父 POM
├── common/                              # 通用模块
│   ├── pom.xml
│   └── src/main/java/com/ticket/common/
│       ├── exception/
│       │   ├── BusinessException.java
│       │   ├── GlobalExceptionHandler.java
│       │   └── ErrorCode.java
│       ├── result/
│       │   └── Result.java
│       ├── util/
│       │   ├── SnowflakeIdGenerator.java
│       │   └── TicketNoGenerator.java
│       └── constant/
│           └── RedisKeys.java
├── core/                                # 核心业务模块
│   ├── pom.xml
│   └── src/main/java/com/ticket/core/
│       ├── domain/entity/               # 9 个实体
│       ├── domain/dto/                  # 3 个 DTO
│       ├── mapper/                      # 9 个 Mapper 接口
│       ├── service/                     # 7 个 Service
│       └── consumer/                    # 1 个消费者
│   └── src/main/resources/mapper/       # 9 个 XML 映射文件
├── admin/                               # 管理端模块
│   ├── pom.xml
│   └── src/main/java/com/ticket/admin/
│       ├── controller/                  # 5 个 Controller
│       └── AdminApplication.java
│   └── src/main/resources/application.yml
├── user/                                # 用户端模块
│   ├── pom.xml
│   └── src/main/java/com/ticket/user/
│       ├── controller/                  # 5 个 Controller
│       ├── config/                      # JWT + Security
│       └── UserApplication.java
│   └── src/main/resources/application.yml
├── payment/                             # 支付模块
│   ├── pom.xml
│   └── src/main/java/com/ticket/payment/
│       ├── gateway/                     # 接口 + Mock
│       ├── service/                     # 支付服务
│       └── PaymentApplication.java
│   └── src/main/resources/application.yml
├── sql/schema.sql                       # 建表 SQL
└── docker-compose.yml                   # 本地开发环境
```

---

## Phase 1: 项目骨架 + Common 模块

> 交付物: 可编译的 Maven 多模块项目骨架,common 模块包含响应封装、异常处理、工具类。

### Task 1.1: 父 POM 和模块结构

创建 `pom.xml`(父 POM) + 5 个子模块 pom.xml + 空包目录。

父 pom.xml 关键点:
- parent: spring-boot-starter-parent 2.7.18
- modules: common, core, admin, user, payment
- dependencyManagement: mybatis 3.5.13, mybatis-spring 2.1.1, redisson 3.23.5, jjwt 0.12.5
- java.version: 11

子模块依赖链:
- common → spring-boot-starter-web, spring-boot-starter-data-redis, redisson-spring-boot-starter
- core → common, mybatis, mybatis-spring, spring-boot-starter-jdbc, mysql-connector-java(runtime)
- admin → core
- user → core, spring-boot-starter-security, jjwt-api
- payment → core

每个子模块 pom.xml 中 admin/user/payment 需要 spring-boot-maven-plugin。

- [ ] 创建所有 pom.xml 文件和空包目录
- [ ] 运行 `mvn compile -q` 确认编译通过(无代码应通过)
- [ ] Commit

### Task 1.2: Common 工具类

创建以下文件(代码见 spec 中的 ErrorCode/Result/BusinessException/GlobalExceptionHandler/SnowflakeIdGenerator/TicketNoGenerator/RedisKeys):

- `common/src/main/java/com/ticket/common/result/Result.java` — 泛型响应封装,code/message/data
- `common/src/main/java/com/ticket/common/exception/ErrorCode.java` — 枚举,包含 SYSTEM_ERROR(500), PARAM_ERROR(400), SEAT_NOT_AVAILABLE(1001), EXCEED_PURCHASE_LIMIT(1002) 等
- `common/src/main/java/com/ticket/common/exception/BusinessException.java` — 继承 RuntimeException,持有 ErrorCode
- `common/src/main/java/com/ticket/common/exception/GlobalExceptionHandler.java` — @RestControllerAdvice,处理 BusinessException 和 Exception
- `common/src/main/java/com/ticket/common/util/SnowflakeIdGenerator.java` — 标准雪花算法,workerId + datacenterId 构造函数
- `common/src/main/java/com/ticket/common/util/TicketNoGenerator.java` — 8位字母数字(TK前缀+6位随机),排除 O/0/I/1
- `common/src/main/java/com/ticket/common/constant/RedisKeys.java` — 静态方法生成 Redis key: sessionSeats(sessionId), seatInfo(seatId), seatLock(sessionId, seatId), sessionPurchase(sessionId, userId), ticketOrderQueue(sessionId), userRateLimit(userId), sessionLock(sessionId)

- [ ] 创建文件
- [ ] Commit

---

## Phase 2: 数据库 + MyBatis 实体层

> 交付物: 数据库建表 SQL,所有 Entity 类,所有 MyBatis Mapper 接口和 XML。

### Task 2.1: 建表 SQL

创建 `sql/schema.sql`,包含 9 张表:

1. `user` — id(BIGINT PK), username, phone, email, password_hash, status, create_time, update_time
2. `user_role` — id(PK AI), user_id, role
3. `show` — id(PK AI), name, description, category, poster_url, venue, status, 时间字段
4. `show_session` — id(PK AI), show_id, name, start_time, end_time, total_seats, limit_per_user, status, 时间字段
5. `seat` — id(PK AI), session_id, row_no, col_no, seat_type, price, status, create_time
6. `order` — id(PK AI), order_no(UK), user_id, session_id, total_amount, status, pay_time, expire_time, 时间字段,索引 idx_status_expire(status, expire_time)
7. `order_item` — id(PK AI), order_id, seat_id, price, seat_info
8. `payment` — id(PK AI), order_id, payment_no(UK), channel, amount, status, trade_no, callback_time
9. `ticket` — id(PK AI), order_id, user_id, qr_code(UK), ticket_no(UK VARCHAR 16), status, verify_time

- [ ] 创建文件
- [ ] Commit

### Task 2.2: Entity 实体类

创建 9 个实体,使用 Lombok @Data,字段与数据库表一一对应:

- `core/.../entity/User.java` — Long id, String username, String phone, String email, String passwordHash, Integer status, LocalDateTime createTime/updateTime
- `core/.../entity/UserRole.java` — Long id, Long userId, String role
- `core/.../entity/Show.java` — Long id, String name, String description, String category, String posterUrl, String venue, Integer status, 时间字段
- `core/.../entity/ShowSession.java` — Long id, Long showId, String name, LocalDateTime startTime/endTime, Integer totalSeats, Integer limitPerUser, Integer status, 时间字段
- `core/.../entity/Seat.java` — Long id, Long sessionId, Integer rowNo, Integer colNo, String seatType, BigDecimal price, Integer status, LocalDateTime createTime
- `core/.../entity/Order.java` — Long id, String orderNo, Long userId, Long sessionId, BigDecimal totalAmount, Integer status, LocalDateTime payTime/expireTime, 时间字段
- `core/.../entity/OrderItem.java` — Long id, Long orderId, Long seatId, BigDecimal price, String seatInfo
- `core/.../entity/Payment.java` — Long id, Long orderId, String paymentNo, String channel, BigDecimal amount, Integer status, String tradeNo, LocalDateTime callbackTime
- `core/.../entity/Ticket.java` — Long id, Long orderId, Long userId, String qrCode, String ticketNo, Integer status, LocalDateTime verifyTime

- [ ] 创建文件
- [ ] Commit

### Task 2.3: MyBatis Mapper 接口 + XML

创建 9 个 Mapper 接口和对应 XML。每个 Mapper 用 @Mapper 注解。

**UserMapper**: insert, selectById, selectByUsername, selectByPhone
**UserRoleMapper**: insert, selectByUserId
**ShowMapper**: insert(useGeneratedKeys), update, selectById, selectAll, selectByStatus
**ShowSessionMapper**: insert(useGeneratedKeys), update, selectById, selectByShowId
**SeatMapper**: insert(useGeneratedKeys), batchInsert(foreach), selectById, selectBySessionId, batchUpdateStatus(foreach)
**OrderMapper**: insert(useGeneratedKeys), selectById, selectByOrderNo, updateStatus, selectExpiredOrders(带 limit), updateStatusAndPayTime
**OrderItemMapper**: insert(useGeneratedKeys), batchInsert(foreach), selectByOrderId, countBySeatIdAndValidOrder(SQL: join order 查 status != 2)
**PaymentMapper**: insert(useGeneratedKeys), selectById, selectByPaymentNo, updateStatus
**TicketMapper**: insert, batchInsert(foreach), selectByQrCode, selectByTicketNo, selectById, selectByOrderId, updateStatusAndVerifyTime

XML 文件放在 `core/src/main/resources/mapper/` 下,命名与接口一致。每个 XML 要有 resultMap。user 表因为保留字需要用反引号 `` `user` ``。order 表同理。

- [ ] 创建所有文件
- [ ] Commit

---

## Phase 3: Core 业务层 — 演出管理 + 库存管理

> 交付物: 演出 CRUD,座位库存 Redis 操作(预热/查询/锁定/释放),限购 Lua 脚本。

### Task 3.1: ShowService

创建 `core/.../service/ShowService.java`:

方法:
- `createShow(Show)` — 设置 status=1,调用 mapper.insert
- `updateShow(Show)` — mapper.update,返回查询结果
- `getShow(Long)` — mapper.selectById
- `listShows(Integer status)` — status 非空调 selectByStatus,否则 selectAll
- `createSession(ShowSession)` — status=0,insert
- `updateSession(ShowSession)` — mapper.update
- `getSession(Long)` — selectById
- `listSessions(Long showId)` — selectByShowId

@Service + 构造器注入。

- [ ] 创建文件
- [ ] Commit

### Task 3.2: SeatInventoryService

创建 `core/.../service/SeatInventoryService.java`:

依赖: StringRedisTemplate

方法:
- `warmup(long sessionId, List<Seat>)` — Pipeline 批量: SADD session:seats 集合 + HMSET seat:info Hash,expire 7天
- `getAvailableSeatIds(long sessionId)` — opsForSet().members()
- `getAvailableCount(long sessionId)` — opsForSet().size()
- `lockSeat(long sessionId, long seatId, String userId, long ttlSeconds)` — setIfAbsent(NX EX)
- `batchLockSeats(long sessionId, List<Long> seatIds, String userId, long ttlSeconds)` — Lua 脚本原子检查+批量 SETNX,任一失败返回 false
- `releaseSeat(long sessionId, long seatId)` — 删 lock key + SADD 回 Set
- `consumeSeat(long sessionId, long seatId)` — SREM + 删 lock key(支付成功后调用)
- `getSeatInfo(long seatId)` — opsForHash().entries()

batchLockSeats 的 Lua 脚本:
```
local sessionId = ARGV[1]
local userId = ARGV[2]
local ttl = ARGV[3]
for i = 4, #ARGV do
  local seatId = ARGV[i]
  local lockKey = 'seat:lock:' .. sessionId .. ':' .. seatId
  local ok = redis.call('SETNX', lockKey, userId)
  if ok == 0 then return 0 end
  redis.call('EXPIRE', lockKey, ttl)
end
return 1
```

- [ ] 创建文件
- [ ] Commit

### Task 3.3: PurchaseLimitService

创建 `core/.../service/PurchaseLimitService.java`:

Lua 脚本(checkAndIncr):
```
local count = redis.call('GET', KEYS[1])
if count == false then count = 0 else count = tonumber(count) end
local limit = tonumber(ARGV[1])
if count >= limit then return 0 end
redis.call('INCR', KEYS[1])
return 1
```

方法:
- `checkAndIncrement(long sessionId, long userId, int limit)` — 执行 Lua,返回 boolean
- `decrement(long sessionId, long userId)` — opsForValue().decrement()
- `getPurchaseCount(long sessionId, long userId)` — 读取 key 值

- [ ] 创建文件
- [ ] Commit

---

## Phase 4: Core 业务层 — 订单 + 支付 + 核验

> 交付物: 订单创建(含超卖兜底)、超时取消、票据生成、入场核验、Redis Stream 消费者。

### Task 4.1: DTO 类

创建:
- `core/.../dto/OrderCreateRequest.java` — Long userId, Long sessionId, List<Long> seatIds
- `core/.../dto/OrderStatusResponse.java` — String orderNo, Integer status, BigDecimal totalAmount, LocalDateTime expireTime, List<String> seatInfos

- [ ] 创建文件
- [ ] Commit

### Task 4.2: OrderService

创建 `core/.../service/OrderService.java`:

依赖: OrderMapper, OrderItemMapper, SeatInventoryService, PurchaseLimitService
SnowflakeIdGenerator(1,1)

方法 `createOrder(OrderCreateRequest)`:
1. 遍历 seatIds,调用 `orderItemMapper.countBySeatIdAndValidOrder(seatId)` 做超卖兜底,已有订单则回滚 Redis(释放所有座位) + 回滚限购计数,抛 SEAT_NOT_AVAILABLE
2. 遍历 seatIds,从 Redis Hash 获取座位信息,计算总价,构建 OrderItem 列表
3. 创建 Order: orderNo(雪花ID), status=0, expireTime=now+5min
4. batchInsert orderItems
5. 返回 order

方法 `cancelOrder(Long orderId)`:
1. 查订单,如果 status != 0 直接返回
2. updateStatus = 2(已取消)
3. 查 orderItems,遍历 releaseSeat
4. decrement 限购计数

方法 `getByOrderNo(String)` — selectByOrderNo
方法 `getOrderItems(Long)` — selectByOrderId

- [ ] 创建文件
- [ ] Commit

### Task 4.3: OrderTimeoutService

创建 `core/.../service/OrderTimeoutService.java`:

@Scheduled(fixedRate = 30000) 每 30 秒:
- 调用 orderMapper.selectExpiredOrders(0, now, 500)
- 遍历调用 orderService.cancelOrder
- catch Exception 打日志,不中断

- [ ] 创建文件
- [ ] Commit

### Task 4.4: TicketService

创建 `core/.../service/TicketService.java`:

方法 `generateTicketsForOrder(Long orderId, Long userId)`:
1. 查 orderItems
2. 每个 item 生成 Ticket: id(雪花ID), qrCode(UUID), ticketNo(TicketNoGenerator.generate + 唯一性校验循环), status=0
3. batchInsert
4. 返回 tickets

- [ ] 创建文件
- [ ] Commit

### Task 4.5: VerifyService

创建 `core/.../service/VerifyService.java`:

方法 `verifyByQrCode(String qrCode)` — 查 ticket,调用 doVerify
方法 `verifyByTicketNo(String ticketNo)` — 查 ticket,调用 doVerify

doVerify(Ticket):
- status==1 → 抛 TICKET_ALREADY_USED("票已使用")
- status==2 → 抛 ORDER_EXPIRED("票已过期")
- updateStatusAndVerifyTime(id, 1, now)
- 重新查询返回

- [ ] 创建文件
- [ ] Commit

### Task 4.6: TicketOrderConsumer

创建 `core/.../consumer/TicketOrderConsumer.java`:

依赖: StringRedisTemplate, OrderService, ObjectMapper

@PostConstruct 启动后台消费线程(Daemon thread)。

完整 Redis Stream 消费逻辑:
- `@Value("${ticket.stream.key:ticket:order:stream}")` 和 `@Value("${ticket.stream.group:order-group}")`
- `startConsumer()`: 创建消费组(try-catch XGROUP CREATE,忽略 BUSYGROUP),启动 Daemon 线程
- `readMessages()`: XREADGROUP GROUP group consumer COUNT 10 BLOCK 2000 STREAMS key >
- `processMessage(MapRecord)`: 反序列化 data(JSON) 为 OrderCreateRequest → orderService.createOrder → XACK
- catch 异常: 记录日志,不 ACK,等待 re-pending
- `shutdown()`: @PreDestroy 设置 running=false,中断线程

> 注: 消费者在此阶段实现完整逻辑,Phase 6 只需做集成验证。

- [ ] 创建文件
- [ ] Commit

---

## Phase 4.5: 单元测试

> 交付物: 核心业务逻辑的单元测试,覆盖正常路径和异常路径。

### Task 4.5.1: SeatInventoryServiceTest

使用 EmbeddedRedis 或 Testcontainers 启动 Redis。

测试用例:
- `warmup_shouldAddSeatsToSet` — warmup 后 getAvailableCount 返回正确数量
- `lockSeat_shouldSucceedWhenAvailable` — lockSeat 返回 true
- `lockSeat_shouldFailWhenAlreadyLocked` — 同一座位第二次 lockSeat 返回 false
- `batchLockSeats_shouldBeAtomic` — Lua 脚本保证原子性,任一失败则全部不锁定
- `releaseSeat_shouldRemoveLockAndAddBackToSet` — release 后可再次锁定

- [ ] 创建测试文件
- [ ] 运行测试
- [ ] Commit

### Task 4.5.2: PurchaseLimitServiceTest

测试用例:
- `checkAndIncrement_shouldAllowWhenUnderLimit` — 限购 5,前 5 次返回 true
- `checkAndIncrement_shouldRejectWhenAtLimit` — 第 6 次返回 false
- `decrement_shouldReduceCount` — decrement 后可再次 increment

- [ ] 创建测试文件
- [ ] 运行测试
- [ ] Commit

### Task 4.5.3: OrderServiceTest

使用 H2 内存数据库 + MyBatis + Mock Redis。

测试用例:
- `createOrder_shouldSucceedWhenSeatsAvailable` — 正常下单,order 和 orderItems 落库
- `createOrder_shouldFailWhenSeatAlreadyOrdered` — 超卖兜底: seatId 已有有效订单时抛 SEAT_NOT_AVAILABLE
- `createOrder_shouldRollbackRedisOnFailure` — 下单失败时释放 Redis 锁和回滚限购计数
- `cancelOrder_shouldReleaseSeats` — 取消后座位锁释放,限购计数减少
- `cancelOrder_shouldNotCancelAlreadyPaidOrder` — status != 0 时直接返回

- [ ] 创建测试文件
- [ ] 运行测试
- [ ] Commit

---

## Phase 5: User 模块 — 认证 + 用户端 API

> 交付物: JWT 认证、注册/登录、演出浏览、购票、支付、核验接口。

### Task 5.1: JWT + Security

创建:
- `user/.../config/JwtTokenProvider.java` — generateToken(userId, username), getUserIdFromToken, validateToken。SECRET 硬编码(生产放配置文件),JJWT 库,HMAC-SHA,30min 过期
- `user/.../config/JwtAuthenticationFilter.java` — OncePerRequestFilter,从 Authorization header 取 Bearer token,验证后设 SecurityContext
- `user/.../config/SecurityConfig.java` — csrf disable, STATELESS, /api/auth/** /api/show/** /api/verify/** permitAll,其余 authenticated,BCryptPasswordEncoder,注册 JwtAuthenticationFilter

- [ ] 创建文件
- [ ] Commit

### Task 5.2: User Controllers + Application + Config

创建:

**AuthController** (/api/auth):
- POST /register — 查用户名是否存在 → insert user → insert userRole(USER) → 返回 token+userId
- POST /login — 查用户+校验密码 → 返回 token+userId

**ShowController** (/api/show):
- GET /list — 返回上架演出
- GET /{id} — 演出详情
- GET /{id}/sessions — 场次列表
- GET /session/{sessionId}/seats — 可售座位集合+数量
- GET /session/{sessionId}/seat/{seatId} — 座位信息 Hash

**OrderController** (/api/order):
- POST /submit — 限购检查(Lua) → 写 Redis Stream → 返回 requestId + QUEUED
- GET /query/{requestId} — 返回占位 OrderStatusResponse(后续完善)

**PaymentController** (/api/payment):
- POST /create — 查订单状态 → 调用 paymentService.processPayment → 调用 ticketService.generateTicketsForOrder → 返回 PAID

**VerifyController** (/api/verify):
- GET /qr/{qrCode} — verifyByQrCode
- GET /ticket/{ticketNo} — verifyByTicketNo

**UserApplication.java** — @SpringBootApplication(scanBasePackages = {"com.ticket.core", "com.ticket.user"}), @MapperScan("com.ticket.core.mapper"), @EnableScheduling

**application.yml** — 公共配置(项目名、mybatis mapper-locations、map-underscore-to-camel-case)
**application-dev.yml** — 开发环境: port 8082, mysql localhost:3306/ticket_system, redis localhost:6379, 日志级别 DEBUG
**application-prod.yml** — 生产环境: port 8082, mysql 连接池配置(HikariCP maxPoolSize=20), redis 集群/哨兵配置, 日志级别 WARN

- [ ] 创建文件
- [ ] Commit

---

## Phase 6: Admin 模块 + Payment 模块 + Docker

> 交付物: 管理端 API、支付模块、docker-compose、端到端验证。

### Task 6.1: Admin Controllers + Application + Config

创建:

**ShowController** (/api/admin/show): createShow, updateShow, getShow, listShows(status 可选)
**SessionController** (/api/admin/session): createSession, updateSession, getSession, listSessions(showId)
**SeatController** (/api/admin/seat): batchCreateSeats, listSeats(sessionId), warmupSeats(sessionId) — 预热时查座位列表调 inventoryService.warmup,然后更新 session.status=1
**OrderController** (/api/admin/order): getOrder, getOrderItems, listOrders(简化分页)
**MonitorController** (/api/admin/monitor): getDashboard(sessionId) — 返回可售座位数

**AdminApplication.java** — @SpringBootApplication(scanBasePackages = {"com.ticket.core", "com.ticket.admin"}), @MapperScan("com.ticket.core.mapper"), @EnableScheduling
**application.yml** — 公共配置(同 user 模块的 mybatis 配置)
**application-dev.yml** — port 8081, mysql localhost:3306/ticket_system, redis localhost:6379, 日志级别 DEBUG
**application-prod.yml** — port 8081, 同 user 的生产配置

- [ ] 创建文件
- [ ] Commit

### Task 6.2: Payment Module

创建:
- `payment/.../gateway/PaymentGateway.java` — 接口,方法 pay(paymentNo, amount) 返回 boolean
- `payment/.../gateway/MockPaymentGateway.java` — @Component("mock"),总是返回 true,打日志
- `payment/.../gateway/PaymentGatewayFactory.java` — 注入 Map<String, PaymentGateway>,getGateway(channel) 返回对应实现,默认返回 mock
- `payment/.../service/PaymentService.java` — processPayment(orderId, channel): 创建 payment 记录 → 调 gateway.pay → 成功则更新 order status=1+payTime,失败则 payment status=2
- `payment/.../PaymentApplication.java` — @SpringBootApplication(scanBasePackages = {"com.ticket.core", "com.ticket.payment"}), @MapperScan("com.ticket.core.mapper")
- `payment/.../application.yml` — 公共配置(同 user 模块的 mybatis 配置)
- `payment/.../application-dev.yml` — port 8083, mysql localhost:3306/ticket_system, redis localhost:6379, 日志级别 DEBUG
- `payment/.../application-prod.yml` — port 8083, 同 user 的生产配置

- [ ] 创建文件
- [ ] Commit

### Task 6.3: Docker Compose

创建 `docker-compose.yml`:
- mysql:8.0, root/root,挂载 volume + 初始化 sql/schema.sql
- redis:7-alpine
- 端口映射 3306, 6379

- [ ] 创建文件
- [ ] 运行 `docker-compose up -d`
- [ ] 验证 mysql 中 ticket_system 数据库和表已创建

### Task 6.4: 端到端验证

按顺序调用 API 验证:
1. POST /api/auth/register
2. POST /api/auth/login (获取 token)
3. POST /api/admin/show/create
4. POST /api/admin/session/create
5. POST /api/admin/seat/batch (批量座位)
6. POST /api/admin/seat/warmup/{sessionId}
7. GET /api/show/list
8. GET /api/show/session/{sessionId}/seats
9. POST /api/order/submit (写 Redis Stream)
10. POST /api/payment/create (Mock 支付)
11. GET /api/verify/qr/{qrCode} (核验)

- [ ] 完成验证
- [ ] Commit

---
