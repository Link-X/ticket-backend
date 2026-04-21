# 抢票系统设计文档

> 日期: 2026-04-20
> 状态: 已批准
> Java: 11 | Spring Boot: 2.7.x

## 概述

中等规模抢票系统(千~万级并发),支持演出管理、选座购票、订单支付、入场核验。
部署目标: 先单机开发,后续支持水平扩展。

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot 2.7.x | JDK 11 |
| ORM | MyBatis | 3.5.x |
| 缓存/锁 | Redis + Redisson | 7.x / 3.x |
| 消息队列 | Redis Stream | - |
| 数据库 | MySQL | 8.x |
| 鉴权 | Spring Security + JWT | - |
| API 文档 | SpringDoc OpenAPI (Boot 2.7 兼容版) | - |
| 构建 | Maven | - |
| 前端(管理端) | Vue 3 + Element Plus | 独立项目 |
| 前端(用户端) | Vue 3 + Vant Mobile | 独立项目 |

## 模块架构

```
common ← core ← admin (管理端 REST API)
               ← user  (用户端 REST API)
               ← payment (支付接口,模拟+预留)
```

- **common**: 通用工具类、异常处理、响应封装、常量定义
- **core**: 核心业务逻辑(演出、场次、座位、订单、支付、核验)
- **admin**: 管理端接口(演出管理、场次管理、座位管理、订单管理、价格策略、数据监控)
- **user**: 用户端接口(用户中心、演出浏览、选座购票、订单支付)
- **payment**: 支付网关接口(Mock/Alipay/WeChat),预留真实支付接入点

## 数据库设计

### 用户模块

**user**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| username | VARCHAR(50) UK | 用户名 |
| phone | VARCHAR(20) UK | 手机号 |
| email | VARCHAR(100) | 邮箱 |
| password_hash | VARCHAR(255) | 密码哈希(BCrypt) |
| status | TINYINT | 0-禁用 1-正常 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**user_role**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 用户ID |
| role | VARCHAR(20) | ADMIN / USER |

### 演出模块

**show**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| name | VARCHAR(100) | 演出名称 |
| description | TEXT | 描述 |
| category | VARCHAR(50) | 分类(演唱会/话剧/体育) |
| poster_url | VARCHAR(500) | 海报URL |
| venue | VARCHAR(200) | 场馆 |
| status | TINYINT | 0-下架 1-上架 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**show_session**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| show_id | BIGINT | 演出ID |
| name | VARCHAR(100) | 场次名称 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| total_seats | INT | 总座位数 |
| limit_per_user | INT | 每人限购数量 |
| status | TINYINT | 0-未开放 1-已预热 2-售票中 3-已售罄 4-已结束 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

**seat** (DB 持久层,座位底表)
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| session_id | BIGINT | 场次ID |
| row_no | INT | 行号 |
| col_no | INT | 列号 |
| seat_type | VARCHAR(20) | VIP/A/普通 |
| price | DECIMAL(10,2) | 价格 |
| status | TINYINT | 0-不可售 1-可售 2-已售 (仅做历史追溯,不参与库存判断) |
| create_time | DATETIME | 创建时间 |

> **seat.status 与 Redis 库存的关系**: Redis Set `session:seats:{session_id}` 是**唯一库存权威**。seat.status 仅在 DB 层记录最终状态(可售/已售),用于管理端查询和历史追溯。抢票过程中的锁定/释放只在 Redis 中完成,不修改 seat.status。订单支付成功后,异步批量更新 seat.status = 2(已售)。两者分工明确: Redis 管实时库存, DB 管持久记录。

### 订单模块

**order**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| order_no | VARCHAR(32) UK | 订单号(雪花ID) |
| user_id | BIGINT | 用户ID |
| session_id | BIGINT | 场次ID |
| total_amount | DECIMAL(10,2) | 订单总额 |
| status | TINYINT | 0-待支付 1-已支付 2-已取消 3-已退款 |
| pay_time | DATETIME | 支付时间 |
| expire_time | DATETIME | 过期时间 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

> 索引: `INDEX idx_status_expire (status, expire_time)` — 用于定时任务批量扫描超时订单

**order_item**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| order_id | BIGINT | 订单ID |
| seat_id | BIGINT | 座位ID |
| price | DECIMAL(10,2) | 单价(快照) |
| seat_info | VARCHAR(100) | 座位信息快照("A排5座") |

> **order 与 seat 的关系**:
> - 订单创建时, Redis 中锁定座位(SREM 扣库存),**不修改** DB 的 seat.status
> - 订单支付成功后,异步批量更新 `seat.status = 2(已售)`,写入 seat_id 与 order 的关联
> - 订单取消时, Redis 中释放座位(SADD 回库存),**不修改** DB 的 seat.status

**order_item 与 ticket 的关系**: 支付成功后,每个 order_item(即每个座位)对应生成一条 ticket 记录。order:ticket = 1:N。

### 支付模块

**payment**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| order_id | BIGINT | 订单ID |
| payment_no | VARCHAR(32) UK | 支付流水号 |
| channel | VARCHAR(20) | MOCK / ALIPAY / WECHAT |
| amount | DECIMAL(10,2) | 支付金额 |
| status | TINYINT | 0-处理中 1-成功 2-失败 |
| trade_no | VARCHAR(100) | 第三方交易号 |
| callback_time | DATETIME | 回调时间 |

### 核验模块

**ticket**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| order_id | BIGINT | 订单ID |
| user_id | BIGINT | 用户ID |
| qr_code | VARCHAR(100) UK | 二维码内容(UUID) |
| ticket_no | VARCHAR(16) UK | 票号(8位字母数字组合,如 `TK7A3B2X`) |
| status | TINYINT | 0-未使用 1-已使用 2-已过期 |
| verify_time | DATETIME | 核销时间 |

> **ticket_no 设计**: 不用雪花ID(19位数字),改用 8 位字母数字随机组合(大写+数字,排除易混淆字符 O/0/I/1)。生成时做唯一性校验,冲突则重新生成。用户友好,可手动输入。

## Redis 设计

### Key 规范

| Key | 类型 | 说明 | TTL |
|-----|------|------|-----|
| `session:seats:{session_id}` | Set | 可售座位ID集合 | 场次结束+1h |
| `seat:info:{seat_id}` | Hash | 座位信息 {row, col, type, price} | 场次结束+1h |
| `seat:lock:{session_id}:{seat_id}` | String | 座位锁(value=user_id) | 5 min |
| `session:purchase:{session_id}:{user_id}` | String | 用户已购数量计数 | 场次结束+1h |
| `queue:ticket:order:{session_id}` | Stream | 购票请求队列 | 消费后自动删除 |
| `rate:user:{user_id}` | ZSet | 用户限流(滑动窗口) | 1 min |

### 关键操作

**库存预热(Pipeline 批量)**
```
MULTI
  SADD session:seats:{session_id} seat_id_1 seat_id_2 ... seat_id_N
  HMSET seat:info:{seat_id_1} row 1 col 5 type A price 100
  HMSET seat:info:{seat_id_2} row 1 col 6 type A price 100
  ...
EXEC
```

**连座锁定(场次锁 + Lua)**
```
1. RLock lock = redisson.getLock("lock:session:{session_id}")
2. lock.lock()
3. 执行 Lua 脚本原子检查 + 锁定多个座位
4. lock.unlock()
```

**单座锁定(直接锁 seat key)**
```
SET seat:lock:{session_id}:{seat_id} {user_id} NX EX 300
```

**限购检查(Lua 脚本原子操作)**
```lua
-- key: session:purchase:{session_id}:{user_id}
-- arg1: limit_per_user
local count = redis.call('GET', KEYS[1])
if count == false then
    count = 0
else
    count = tonumber(count)
end
if count >= tonumber(ARGV[1]) then
    return -1  -- 超出限购
end
redis.call('INCR', KEYS[1])
return 1  -- 成功
```

> 用 Lua 脚本保证检查和递增的原子性,避免 INCR+DECR 分离导致的竞态条件。

**库存计数**
```
可用库存 = SCARD session:seats:{session_id}
```

## 核心业务流程

### 购票流程(经过 MQ 削峰)

```
用户选座 → Lua 限购检查 → 限流检查 → 发送请求到 Redis Stream
                                                              ↓
                                                   返回 "排队中" + requestId
                                                              ↓
                                                   MQ 消费者消费消息
                                                              ↓
                                              ┌─ Lua 检查座位可用性(连座用场次锁)
                                              ├─ SET seat:lock (NX EX 300)
                                              ├─ SREM session:seats 扣库存
                                              ├─ DB 创建订单(PENDING, expire 5min)
                                              └─ Redisson DelayedQueue 加入超时任务
                                                              ↓
                                                   用户轮询订单状态(/api/order/query/{requestId})
                                                              ↓
                                ┌─────────────────────────────┴─────────────────────────────┐
                                ↓                                                           ↓
                          支付成功                                                      超时未支付(5min)
                        ┌─────────────┐                                              MQ 取消任务
                        │ 更新订单状态 │                                          ┌────────────────┐
                        │ 生成票据记录 │                                          │ 订单→CANCELLED │
                        │ 异步更新 seat │                                         │ 删除 seat:lock │
                        │  .status=已售  │                                        │ SADD 回库存    │
                        │ 删除 seat:lock │                                         └────────────────┘
                        └─────────────┘
```

### 订单超时取消(双保障)

**主路径**: Redisson DelayedQueue — 订单创建时放入,到期触发取消回调

**兜底**: 定时任务每 30 秒执行:
```sql
SELECT id, session_id FROM `order`
WHERE status = 0 AND expire_time < NOW()
LIMIT 500
```
批量取消,利用 `idx_status_expire` 索引,避免全表扫描。

两个路径互为兜底,Redis 故障时定时任务保证最终一致性。

### 库存预热流程

1. 管理端创建场次 → 添加座位数据到 DB
2. 管理端点击「预热」→ 后端批量 Pipeline 写入 Redis
3. 预热完成 → 更新 `show_session.status = 1 (已预热)` → 用户端可见
4. 万级座位预热时间 < 3s

### 支付流程

1. 用户选择支付方式 → 调用 `/api/payment/create`
2. Mock 模式: 直接更新支付状态为成功
3. 预留接口: `PaymentGateway` 接口,后续实现 `AlipayGateway` / `WechatGateway`
4. 支付成功 → 回调更新订单 → 生成 ticket 记录 → 异步更新 seat.status

### 入场核验流程

1. 工作人员打开核验端 → 扫码 / 输入票号
2. 调用 `/api/verify/qr/{qrCode}` 或 `/api/verify/ticket/{ticketNo}`
3. 更新 ticket.status = 1,记录 verify_time
4. 返回用户信息及座位信息

## 高并发设计

### 防超卖
- Redis Set 原子操作(SREM)扣减库存,不存在竞态
- DB 层面 order 创建成功后,异步更新 seat.status 做最终一致性保障
- 不依赖 DB 余票字段,从根源消除不一致

### 限流
- 用户级: Redis ZSet 滑动窗口,每秒最多 5 次请求
- 场次级: Redis Stream 队列长度限制,超出返回"系统繁忙"

### 防重放
- 前端按钮禁用 + 后端 requestId 幂等
- Redis Stream 消息带 requestId,消费者去重

### 队列削峰
- Redis Stream 作为轻量消息队列,不引入 RabbitMQ/RocketMQ
- 消费者数量可配置(默认 4-8 个),单机处理能力 ~500 QPS
- 后续扩展: 消费者可独立部署为独立服务

### 锁粒度
- 单座购买: 直接锁 `seat:lock:{session_id}:{seat_id}`,冲突小
- 连座购买: 锁 `lock:session:{session_id}`,Lua 脚本原子批量操作
- 连座并发量天然低,场次锁影响可控

### 限购
- Lua 脚本原子执行 INCR + 检查,避免分离操作的竞态

## 异常处理

### Redis 不可用
- **降级**: 抢购核心逻辑依赖 Redis,Redis 不可用时直接返回"系统繁忙",不降级到 DB 层(保证一致性)
- **告警**: 触发 P0 告警,通知运维
- **恢复后**: 检查 DB 与 Redis 库存差异,通过管理端「重新预热」恢复

### 数据库写入失败
- 订单创建 DB 失败时,回滚 Redis 操作(SADD 回库存,DECR 限购计数)
- 返回"下单失败,请重试"
- 记录错误日志,触发告警

### MQ 消费者异常
- 消费者抛出异常时,消息不 ACK,自动重新入队
- 同一消息重试 3 次后进入死信队列,记录日志供人工处理
- Redis Stream 的 `XREADGROUP` 支持自动 re-pending

### 支付回调超时
- 第三方支付回调超时未到达时,依赖订单超时取消机制释放座位
- 用户可在订单页面手动点击"确认支付"(查询支付状态)

### 票据重复核验
- 同一 ticket 多次核验时,返回"票已使用"并记录重复核验日志
- 不抛出异常,返回明确错误信息

### 超卖兜底
- 即使 Redis 层面出现极端 bug 导致超卖,订单创建时 DB 校验:
  ```sql
  SELECT COUNT(*) FROM order_item oi
  JOIN `order` o ON oi.order_id = o.id
  WHERE oi.seat_id = ? AND o.status != 2  -- 未取消的订单
  ```
  若已存在该座位的有效订单,则拒绝创建新订单,回滚 Redis 并返回错误

## 部署架构

```
                        ┌──────────────────┐
                        │     Nginx        │
                        │  (反向代理/SSL)   │
                        └────────┬─────────┘
                                 │
                    ┌────────────┴────────────┐
                    │                         │
           ┌────────▼────────┐      ┌────────▼────────┐
           │  admin:8081     │      │   user:8082     │
           │  Spring Boot    │      │  Spring Boot    │
           │  (管理端)        │      │  (用户端)        │
           └────────┬────────┘      └────────┬────────┘
                    │                         │
                    └────────────┬────────────┘
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
     ┌────────▼────────┐ ┌──────▼───────┐ ┌────────▼────────┐
     │   MySQL 8       │ │   Redis 7    │ │   Redis Stream  │
     │   (主从可选)     │ │  (缓存/锁)   │ │  (消息队列)     │
     └─────────────────┘ └──────────────┘ └─────────────────┘
```

- 单机部署: 所有组件在同一台机器,端口区分 admin(8081) / user(8082)
- 扩展部署: Nginx 负载多个 user 实例,MySQL 主从,Redis 哨兵/Sentinel
- Docker Compose 一键启动本地开发环境

## 监控告警

### 应用监控(Spring Boot Actuator + Prometheus + Grafana)
| 指标 | 告警阈值 | 级别 |
|------|----------|------|
| JVM 堆使用率 | > 85% 持续 5min | P1 |
| 接口 P99 延迟 | > 3s 持续 5min | P1 |
| 接口错误率 | > 5% 持续 2min | P1 |
| 数据库连接池使用率 | > 90% 持续 3min | P1 |
| Redis 连接数 | 接近上限 | P1 |

### 业务监控
| 指标 | 告警阈值 | 级别 |
|------|----------|------|
| 订单支付超时率 | > 20% 持续 10min | P1 |
| Redis 库存与 DB 差异 | > 0 | P0 |
| MQ 消息堆积 | > 5000 条持续 5min | P1 |
| 超时订单未释放 | > 100 条 | P2 |
| 支付失败率 | > 10% 持续 5min | P1 |

### 告警方式
- P0: 短信 + 邮件 + 企业微信/钉钉
- P1: 邮件 + 企业微信/钉钉
- P2: 邮件(可积累)

## 数据监控

近实时(分钟级),定时任务聚合数据,管理端查询接口返回:
- 实时售票率(已售 / 总座位)
- 各票价区段销量分布
- 订单转化率(浏览→下单→支付)
- 异常指标(超时订单占比、支付失败率)

## 压测目标

| 指标 | 目标值 |
|------|--------|
| 单座 QPS(Redis 层) | 1000+ |
| 下单 QPS(MQ 削峰后) | 200-500 |
| P99 响应时间(查询) | < 500ms |
| P99 响应时间(下单) | < 2s |
| 座位锁定成功率 | > 99.9% |
| 订单超时释放延迟 | < 10s |
| 单机支持并发用户 | 5000+ |
| 预热时间(万座) | < 3s |

## 安全设计

- 密码: BCrypt 加密存储
- JWT: access_token(30min) + refresh_token(7d)
- 接口限流: 用户级 + IP 级
- SQL 注入防护: MyBatis `#{}` 参数化查询
- XSS 防护: 输入过滤 + 输出编码
- 支付金额校验: 后端重新计算,不使用前端传递的金额

## 后续扩展方向

- 微服务拆分: admin/user/payment 各自独立部署
- 引入 RabbitMQ/RocketMQ 替代 Redis Stream(更大规模时)
- 真实支付接入: 实现 `PaymentGateway` 接口
- CDN 加速: 演出海报等静态资源
- 分库分表: 订单表数据量大时按 session_id 分片
