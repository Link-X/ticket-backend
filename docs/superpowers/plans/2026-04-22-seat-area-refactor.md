# 座位区域化重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将座位体系从"单座单价"改造为"区域定价 + 情侣连座 + 网格化座位图"，返回结构包含 `areaPriceList` 和 `seatSection`。

**Architecture:** 新增 `seat_area` 表管理每场次的价格区域；`seat` 表去掉独立 price，改用 `type`（普通/情侣左/情侣右）+ `area_id` + `pair_seat_id`；Redis warmup 缓存 type/areaId 及区域价格；前端请求 `/session/{sessionId}/seats` 时返回网格化座位图和价格列表，座位实时 status 从 Redis 读取。

**Tech Stack:** Java 17, Spring Boot, MyBatis, MySQL 8.0, Redis (StringRedisTemplate), Lombok

---

## 文件清单

| 操作 | 文件路径 |
|------|---------|
| 修改 | `sql/schema.sql` |
| 新增 | `core/.../domain/entity/SeatArea.java` |
| 修改 | `core/.../domain/entity/Seat.java` |
| 修改 | `core/.../domain/entity/ShowSession.java` |
| 新增 | `core/.../domain/vo/AreaPriceVO.java` |
| 新增 | `core/.../domain/vo/SeatColVO.java` |
| 新增 | `core/.../domain/vo/SeatRowVO.java` |
| 新增 | `core/.../domain/vo/SeatSectionVO.java` |
| 新增 | `core/.../domain/vo/SessionSeatResponse.java` |
| 新增 | `core/.../mapper/SeatAreaMapper.java` |
| 新增 | `core/.../resources/mapper/SeatAreaMapper.xml` |
| 修改 | `core/.../mapper/SeatMapper.java` |
| 修改 | `core/.../resources/mapper/SeatMapper.xml` |
| 修改 | `core/.../resources/mapper/ShowSessionMapper.xml` |
| 修改 | `core/.../domain/entity/ShowSession.java` (已在上方列出) |
| 修改 | `common/.../constant/RedisKeys.java` |
| 新增 | `core/.../service/SeatAreaService.java` |
| 修改 | `core/.../service/SeatInventoryService.java` |
| 修改 | `core/.../service/ShowService.java` |
| 修改 | `core/.../service/OrderService.java` |
| 修改 | `admin/.../controller/SeatController.java` |
| 修改 | `user/.../controller/ShowController.java` |

---

### Task 1: 数据库 Schema 变更

**Files:**
- Modify: `sql/schema.sql`

- [ ] **Step 1: 在 schema.sql 末尾追加变更 SQL**

将以下内容添加到 `sql/schema.sql` 文件末尾：

```sql
-- ===== 座位区域化重构变更 =====

-- 新增：座位价格区域表
CREATE TABLE seat_area (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL COMMENT '关联场次ID',
    area_id     VARCHAR(32)  NOT NULL COMMENT '区域标识(如 0、1，场次内唯一)',
    price       DECIMAL(10,2) NOT NULL COMMENT '区域售价',
    origin_price DECIMAL(10,2) NOT NULL COMMENT '区域原价',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_area (session_id, area_id),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位价格区域表';

-- 修改 show_session：新增网格尺寸字段
ALTER TABLE show_session
    ADD COLUMN row_count INT NOT NULL DEFAULT 0 COMMENT '座位网格行数',
    ADD COLUMN col_count INT NOT NULL DEFAULT 0 COMMENT '座位网格列数';

-- 修改 seat：去掉 seat_type/price，改用 type/area_id/seat_name/pair_seat_id
ALTER TABLE seat
    DROP COLUMN seat_type,
    DROP COLUMN price,
    ADD COLUMN type         INT          NOT NULL DEFAULT 1  COMMENT '座位类型: 1=普通, 2=情侣左, 3=情侣右',
    ADD COLUMN area_id      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '价格区域ID，对应 seat_area.area_id',
    ADD COLUMN seat_name    VARCHAR(64)  DEFAULT NULL        COMMENT '座位名称，如 1排01座',
    ADD COLUMN pair_seat_id BIGINT       DEFAULT NULL        COMMENT '情侣连座配对座位ID，type=2/3时非空';
```

---

### Task 2: 实体类变更

**Files:**
- Modify: `core/src/main/java/com/ticket/core/domain/entity/Seat.java`
- Modify: `core/src/main/java/com/ticket/core/domain/entity/ShowSession.java`
- Create: `core/src/main/java/com/ticket/core/domain/entity/SeatArea.java`

- [ ] **Step 1: 新建 SeatArea 实体**

```java
// 文件：core/src/main/java/com/ticket/core/domain/entity/SeatArea.java
package com.ticket.core.domain.entity;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SeatArea {
    private Long id;
    private Long sessionId;
    /** 区域标识，如 "0"、"1"，场次内唯一 */
    private String areaId;
    private BigDecimal price;
    private BigDecimal originPrice;
}
```

- [ ] **Step 2: 修改 Seat 实体（去掉 seatType/price，新增 type/areaId/seatName/pairSeatId）**

```java
// 文件：core/src/main/java/com/ticket/core/domain/entity/Seat.java
package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Seat {
    private Long id;
    private Long sessionId;
    private Integer rowNo;
    private Integer colNo;
    /** 座位类型: 1=普通, 2=情侣左, 3=情侣右 */
    private Integer type;
    /** 对应 seat_area.area_id */
    private String areaId;
    private String seatName;
    /** 情侣连座配对座位ID，type=2/3时非空 */
    private Long pairSeatId;
    /** 0=可售, 1=已锁, 2=已售 */
    private Integer status;
    private LocalDateTime createTime;
}
```

- [ ] **Step 3: 修改 ShowSession 实体（新增 rowCount/colCount）**

```java
// 文件：core/src/main/java/com/ticket/core/domain/entity/ShowSession.java
package com.ticket.core.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ShowSession {
    private Long id;
    private Long showId;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer totalSeats;
    private Integer limitPerUser;
    private Integer status;
    /** 座位网格总行数 */
    private Integer rowCount;
    /** 座位网格总列数 */
    private Integer colCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

---

### Task 3: SeatArea Mapper

**Files:**
- Create: `core/src/main/java/com/ticket/core/mapper/SeatAreaMapper.java`
- Create: `core/src/main/resources/mapper/SeatAreaMapper.xml`

- [ ] **Step 1: 新建 SeatAreaMapper 接口**

```java
// 文件：core/src/main/java/com/ticket/core/mapper/SeatAreaMapper.java
package com.ticket.core.mapper;

import com.ticket.core.domain.entity.SeatArea;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatAreaMapper {

    int batchInsert(@Param("areas") List<SeatArea> areas);

    List<SeatArea> selectBySessionId(Long sessionId);

    int deleteBySessionId(Long sessionId);
}
```

- [ ] **Step 2: 新建 SeatAreaMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.ticket.core.mapper.SeatAreaMapper">

    <resultMap id="SeatAreaResultMap" type="com.ticket.core.domain.entity.SeatArea">
        <id     column="id"           property="id"/>
        <result column="session_id"   property="sessionId"/>
        <result column="area_id"      property="areaId"/>
        <result column="price"        property="price"/>
        <result column="origin_price" property="originPrice"/>
    </resultMap>

    <insert id="batchInsert" useGeneratedKeys="true" keyProperty="areas.id">
        INSERT INTO seat_area (session_id, area_id, price, origin_price)
        VALUES
        <foreach collection="areas" item="a" separator=",">
            (#{a.sessionId}, #{a.areaId}, #{a.price}, #{a.originPrice})
        </foreach>
    </insert>

    <select id="selectBySessionId" resultMap="SeatAreaResultMap">
        SELECT id, session_id, area_id, price, origin_price
        FROM seat_area
        WHERE session_id = #{sessionId}
        ORDER BY area_id ASC
    </select>

    <delete id="deleteBySessionId">
        DELETE FROM seat_area WHERE session_id = #{sessionId}
    </delete>

</mapper>
```

---

### Task 4: 修改 SeatMapper

**Files:**
- Modify: `core/src/main/java/com/ticket/core/mapper/SeatMapper.java`
- Modify: `core/src/main/resources/mapper/SeatMapper.xml`

- [ ] **Step 1: 更新 SeatMapper 接口（新增 selectByIds 方法）**

```java
// 文件：core/src/main/java/com/ticket/core/mapper/SeatMapper.java
package com.ticket.core.mapper;

import com.ticket.core.domain.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatMapper {

    int insert(Seat seat);

    int batchInsert(@Param("seats") List<Seat> seats);

    Seat selectById(Long id);

    List<Seat> selectBySessionId(Long sessionId);

    /** 根据 ID 列表批量查询（用于下单时获取 areaId 和 pairSeatId） */
    List<Seat> selectByIds(@Param("ids") List<Long> ids);

    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);
}
```

- [ ] **Step 2: 更新 SeatMapper.xml（对应新字段，新增 selectByIds）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.ticket.core.mapper.SeatMapper">

    <resultMap id="SeatResultMap" type="com.ticket.core.domain.entity.Seat">
        <id     column="id"           property="id"/>
        <result column="session_id"   property="sessionId"/>
        <result column="row_no"       property="rowNo"/>
        <result column="col_no"       property="colNo"/>
        <result column="type"         property="type"/>
        <result column="area_id"      property="areaId"/>
        <result column="seat_name"    property="seatName"/>
        <result column="pair_seat_id" property="pairSeatId"/>
        <result column="status"       property="status"/>
        <result column="create_time"  property="createTime"/>
    </resultMap>

    <insert id="insert" parameterType="com.ticket.core.domain.entity.Seat" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO seat (session_id, row_no, col_no, type, area_id, seat_name, pair_seat_id, status, create_time)
        VALUES (#{sessionId}, #{rowNo}, #{colNo}, #{type}, #{areaId}, #{seatName}, #{pairSeatId}, #{status}, #{createTime})
    </insert>

    <insert id="batchInsert" useGeneratedKeys="true" keyProperty="seats.id">
        INSERT INTO seat (session_id, row_no, col_no, type, area_id, seat_name, pair_seat_id, status, create_time)
        VALUES
        <foreach collection="seats" item="seat" separator=",">
            (#{seat.sessionId}, #{seat.rowNo}, #{seat.colNo}, #{seat.type}, #{seat.areaId},
             #{seat.seatName}, #{seat.pairSeatId}, #{seat.status}, #{seat.createTime})
        </foreach>
    </insert>

    <select id="selectById" resultMap="SeatResultMap">
        SELECT id, session_id, row_no, col_no, type, area_id, seat_name, pair_seat_id, status, create_time
        FROM seat WHERE id = #{id}
    </select>

    <select id="selectBySessionId" resultMap="SeatResultMap">
        SELECT id, session_id, row_no, col_no, type, area_id, seat_name, pair_seat_id, status, create_time
        FROM seat
        WHERE session_id = #{sessionId}
        ORDER BY row_no ASC, col_no ASC
    </select>

    <select id="selectByIds" resultMap="SeatResultMap">
        SELECT id, session_id, row_no, col_no, type, area_id, seat_name, pair_seat_id, status, create_time
        FROM seat
        WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </select>

    <update id="batchUpdateStatus">
        UPDATE seat SET status = #{status}
        WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </update>

</mapper>
```

---

### Task 5: 修改 ShowSessionMapper.xml（新增 rowCount/colCount 字段）

**Files:**
- Modify: `core/src/main/resources/mapper/ShowSessionMapper.xml`

- [ ] **Step 1: 更新 ShowSessionMapper.xml，同步 rowCount/colCount 字段**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="com.ticket.core.mapper.ShowSessionMapper">

    <resultMap id="ShowSessionResultMap" type="com.ticket.core.domain.entity.ShowSession">
        <id     column="id"            property="id"/>
        <result column="show_id"       property="showId"/>
        <result column="name"          property="name"/>
        <result column="start_time"    property="startTime"/>
        <result column="end_time"      property="endTime"/>
        <result column="total_seats"   property="totalSeats"/>
        <result column="limit_per_user" property="limitPerUser"/>
        <result column="status"        property="status"/>
        <result column="row_count"     property="rowCount"/>
        <result column="col_count"     property="colCount"/>
        <result column="create_time"   property="createTime"/>
        <result column="update_time"   property="updateTime"/>
    </resultMap>

    <insert id="insert" parameterType="com.ticket.core.domain.entity.ShowSession" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO show_session (show_id, name, start_time, end_time, total_seats, limit_per_user, status, row_count, col_count, create_time, update_time)
        VALUES (#{showId}, #{name}, #{startTime}, #{endTime}, #{totalSeats}, #{limitPerUser}, #{status},
                #{rowCount}, #{colCount}, #{createTime}, #{updateTime})
    </insert>

    <update id="update" parameterType="com.ticket.core.domain.entity.ShowSession">
        UPDATE show_session
        SET show_id        = #{showId},
            name           = #{name},
            start_time     = #{startTime},
            end_time       = #{endTime},
            total_seats    = #{totalSeats},
            limit_per_user = #{limitPerUser},
            status         = #{status},
            row_count      = #{rowCount},
            col_count      = #{colCount},
            update_time    = #{updateTime}
        WHERE id = #{id}
    </update>

    <select id="selectById" resultMap="ShowSessionResultMap">
        SELECT id, show_id, name, start_time, end_time, total_seats, limit_per_user, status,
               row_count, col_count, create_time, update_time
        FROM show_session WHERE id = #{id}
    </select>

    <select id="selectByShowId" resultMap="ShowSessionResultMap">
        SELECT id, show_id, name, start_time, end_time, total_seats, limit_per_user, status,
               row_count, col_count, create_time, update_time
        FROM show_session WHERE show_id = #{showId}
        ORDER BY start_time ASC
    </select>

</mapper>
```

---

### Task 6: VO 类（前端响应结构）

**Files:**
- Create: `core/src/main/java/com/ticket/core/domain/vo/AreaPriceVO.java`
- Create: `core/src/main/java/com/ticket/core/domain/vo/SeatColVO.java`
- Create: `core/src/main/java/com/ticket/core/domain/vo/SeatRowVO.java`
- Create: `core/src/main/java/com/ticket/core/domain/vo/SeatSectionVO.java`
- Create: `core/src/main/java/com/ticket/core/domain/vo/SessionSeatResponse.java`

- [ ] **Step 1: AreaPriceVO**

```java
// 文件：core/src/main/java/com/ticket/core/domain/vo/AreaPriceVO.java
package com.ticket.core.domain.vo;

import lombok.Data;

@Data
public class AreaPriceVO {
    private String areaId;
    private String price;
    private String originPrice;
}
```

- [ ] **Step 2: SeatColVO（单个座位列信息，type=0 时为空位占位）**

```java
// 文件：core/src/main/java/com/ticket/core/domain/vo/SeatColVO.java
package com.ticket.core.domain.vo;

import lombok.Data;

@Data
public class SeatColVO {
    /** 座位数据库ID，空位时为空字符串 */
    private String colId;
    /** 列号，空位时为空字符串 */
    private String colNum;
    /** 座位名称，如 "1排01座"；空位时为 null */
    private String seatName;
    /**
     * 座位类型: 0=空位(占位), 1=普通, 2=情侣左, 3=情侣右
     * type=0 仅用于前端网格占位，不可购买，无 status
     */
    private Integer type;
    /** 价格区域ID；空位时为 null */
    private String areaId;
    /**
     * 座位实时状态（type=0 时为 null）
     * 0=可售, 1=已锁, 2=已售
     */
    private Integer status;
}
```

- [ ] **Step 3: SeatRowVO**

```java
// 文件：core/src/main/java/com/ticket/core/domain/vo/SeatRowVO.java
package com.ticket.core.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class SeatRowVO {
    private String rowsId;
    private String rowsNum;
    private List<SeatColVO> columns;
}
```

- [ ] **Step 4: SeatSectionVO**

```java
// 文件：core/src/main/java/com/ticket/core/domain/vo/SeatSectionVO.java
package com.ticket.core.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class SeatSectionVO {
    private Integer rowCount;
    private Integer columnCount;
    private List<SeatRowVO> seatRows;
}
```

- [ ] **Step 5: SessionSeatResponse（最终返回给前端的响应体）**

```java
// 文件：core/src/main/java/com/ticket/core/domain/vo/SessionSeatResponse.java
package com.ticket.core.domain.vo;

import lombok.Data;
import java.util.List;

@Data
public class SessionSeatResponse {
    private List<AreaPriceVO> areaPriceList;
    private SeatSectionVO seatSection;
}
```

---

### Task 7: RedisKeys 新增 + SeatInventoryService 修改

**Files:**
- Modify: `common/src/main/java/com/ticket/common/constant/RedisKeys.java`
- Modify: `core/src/main/java/com/ticket/core/service/SeatInventoryService.java`

- [ ] **Step 1: RedisKeys 新增 seatAreaPrice 方法**

在 `RedisKeys.java` 末尾（`sessionLock` 方法之后）新增：

```java
    /** 场次内某区域的价格信息 Hash（字段: price, originPrice） */
    public static String seatAreaPrice(long sessionId, String areaId) {
        return "session:area:price:" + sessionId + ":" + areaId;
    }
```

- [ ] **Step 2: 修改 SeatInventoryService.warmup，缓存 type/areaId 及区域价格**

将整个 `warmup` 方法替换为：

```java
/**
 * 预热座位库存：Pipeline 批量写入座位集合及各座位 Hash 信息，同时缓存区域价格
 *
 * @param sessionId 场次 ID
 * @param seats     需要写入的座位列表（type != 0）
 * @param areas     需要缓存的价格区域列表
 */
public void warmup(long sessionId, List<Seat> seats, List<SeatArea> areas) {
    String sessionKey = RedisKeys.sessionSeats(sessionId);

    redisTemplate.executePipelined((RedisConnection connection) -> {
        // 批量 SADD 所有 seatId 到场次座位集合
        byte[] sessionKeyBytes = sessionKey.getBytes(StandardCharsets.UTF_8);
        for (Seat seat : seats) {
            connection.sAdd(sessionKeyBytes,
                    String.valueOf(seat.getId()).getBytes(StandardCharsets.UTF_8));
        }
        connection.expire(sessionKeyBytes, INVENTORY_TTL_SECONDS);

        // 为每个座位写入 Hash 信息（存 type 和 areaId，不再存 price）
        for (Seat seat : seats) {
            String seatInfoKey = RedisKeys.seatInfo(seat.getId());
            byte[] seatInfoKeyBytes = seatInfoKey.getBytes(StandardCharsets.UTF_8);

            Map<byte[], byte[]> seatInfoMap = new HashMap<>();
            seatInfoMap.put("row".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(seat.getRowNo()).getBytes(StandardCharsets.UTF_8));
            seatInfoMap.put("col".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(seat.getColNo()).getBytes(StandardCharsets.UTF_8));
            seatInfoMap.put("type".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(seat.getType()).getBytes(StandardCharsets.UTF_8));
            seatInfoMap.put("areaId".getBytes(StandardCharsets.UTF_8),
                    (seat.getAreaId() != null ? seat.getAreaId() : "").getBytes(StandardCharsets.UTF_8));

            connection.hMSet(seatInfoKeyBytes, seatInfoMap);
            connection.expire(seatInfoKeyBytes, INVENTORY_TTL_SECONDS);
        }

        // 缓存区域价格（Hash: price, originPrice）
        for (SeatArea area : areas) {
            String areaKey = RedisKeys.seatAreaPrice(sessionId, area.getAreaId());
            byte[] areaKeyBytes = areaKey.getBytes(StandardCharsets.UTF_8);

            Map<byte[], byte[]> areaMap = new HashMap<>();
            areaMap.put("price".getBytes(StandardCharsets.UTF_8),
                    area.getPrice().toPlainString().getBytes(StandardCharsets.UTF_8));
            areaMap.put("originPrice".getBytes(StandardCharsets.UTF_8),
                    area.getOriginPrice().toPlainString().getBytes(StandardCharsets.UTF_8));

            connection.hMSet(areaKeyBytes, areaMap);
            connection.expire(areaKeyBytes, INVENTORY_TTL_SECONDS);
        }
        return null;
    });
}
```

同时在 `SeatInventoryService` 类顶部 import 中新增：

```java
import com.ticket.core.domain.entity.SeatArea;
```

- [ ] **Step 3: 新增 getSeatStatus 方法（查询单个座位实时状态）**

在 `getSeatInfo` 方法之后新增：

```java
/**
 * 批量查询座位实时状态
 * 规则：不在可售集合 → 2(已售)；在可售集合且有锁 → 1(已锁)；否则 → 0(可售)
 *
 * @param sessionId 场次 ID
 * @param seatIds   待查询的座位 ID 列表
 * @return Map<seatId, status>
 */
public Map<Long, Integer> batchGetSeatStatus(long sessionId, List<Long> seatIds) {
    Set<String> availableSet = getAvailableSeatIds(sessionId);

    // Pipeline 批量检查锁 key 是否存在
    List<Long> inAvailable = new ArrayList<>();
    for (Long seatId : seatIds) {
        if (availableSet != null && availableSet.contains(String.valueOf(seatId))) {
            inAvailable.add(seatId);
        }
    }

    // 批量 EXISTS 检查锁
    List<Object> existsResults = redisTemplate.executePipelined((RedisConnection connection) -> {
        for (Long seatId : inAvailable) {
            String lockKey = RedisKeys.seatLock(sessionId, seatId);
            connection.exists(lockKey.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    });

    // 组装结果
    Map<Long, Boolean> lockedMap = new HashMap<>();
    for (int i = 0; i < inAvailable.size(); i++) {
        Object result = existsResults.get(i);
        boolean locked = result instanceof Long && (Long) result > 0;
        lockedMap.put(inAvailable.get(i), locked);
    }

    Map<Long, Integer> statusMap = new HashMap<>();
    for (Long seatId : seatIds) {
        if (availableSet == null || !availableSet.contains(String.valueOf(seatId))) {
            statusMap.put(seatId, 2); // 已售
        } else if (Boolean.TRUE.equals(lockedMap.get(seatId))) {
            statusMap.put(seatId, 1); // 已锁
        } else {
            statusMap.put(seatId, 0); // 可售
        }
    }
    return statusMap;
}

/**
 * 从 Redis 获取区域价格（warmup 后可用）
 *
 * @param sessionId 场次 ID
 * @param areaId    区域 ID
 * @return price 字符串，未命中返回 null
 */
public String getAreaPrice(long sessionId, String areaId) {
    String key = RedisKeys.seatAreaPrice(sessionId, areaId);
    Object price = redisTemplate.opsForHash().get(key, "price");
    return price != null ? (String) price : null;
}
```

---

### Task 8: SeatAreaService

**Files:**
- Create: `core/src/main/java/com/ticket/core/service/SeatAreaService.java`

- [ ] **Step 1: 新建 SeatAreaService**

```java
// 文件：core/src/main/java/com/ticket/core/service/SeatAreaService.java
package com.ticket.core.service;

import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.mapper.SeatAreaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeatAreaService {

    private final SeatAreaMapper seatAreaMapper;

    public SeatAreaService(SeatAreaMapper seatAreaMapper) {
        this.seatAreaMapper = seatAreaMapper;
    }

    /**
     * 保存场次价格区域（覆盖写：先删旧的再批量插入）
     */
    @Transactional
    public void saveAreas(Long sessionId, List<SeatArea> areas) {
        seatAreaMapper.deleteBySessionId(sessionId);
        areas.forEach(a -> a.setSessionId(sessionId));
        if (!areas.isEmpty()) {
            seatAreaMapper.batchInsert(areas);
        }
    }

    /**
     * 查询场次价格区域列表
     */
    public List<SeatArea> getAreasBySession(Long sessionId) {
        return seatAreaMapper.selectBySessionId(sessionId);
    }
}
```

---

### Task 9: ShowService 新增 getSeatSection 方法

**Files:**
- Modify: `core/src/main/java/com/ticket/core/service/ShowService.java`

- [ ] **Step 1: 新增 import 和 getSeatSection 方法**

在 `ShowService` 类顶部新增 import：

```java
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.vo.*;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.mapper.SeatAreaMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
```

构造器注入新增 `SeatMapper` 和 `SeatAreaMapper`（同时注入 `SeatInventoryService`）：

```java
private final ShowMapper showMapper;
private final ShowSessionMapper showSessionMapper;
private final SeatMapper seatMapper;
private final SeatAreaMapper seatAreaMapper;
private final SeatInventoryService inventoryService;

public ShowService(ShowMapper showMapper,
                   ShowSessionMapper showSessionMapper,
                   SeatMapper seatMapper,
                   SeatAreaMapper seatAreaMapper,
                   SeatInventoryService inventoryService) {
    this.showMapper = showMapper;
    this.showSessionMapper = showSessionMapper;
    this.seatMapper = seatMapper;
    this.seatAreaMapper = seatAreaMapper;
    this.inventoryService = inventoryService;
}
```

在类末尾追加 `getSeatSection` 方法：

```java
/**
 * 查询场次的完整座位图和价格区域列表
 * - areaPriceList：该场次所有价格区域
 * - seatSection：按 rowCount×colCount 网格构建，空位以 type=0 占位
 * - 座位 status 从 Redis 实时读取
 */
public SessionSeatResponse getSeatSection(Long sessionId) {
    ShowSession session = showSessionMapper.selectById(sessionId);
    List<SeatArea> areas = seatAreaMapper.selectBySessionId(sessionId);
    List<Seat> seats = seatMapper.selectBySessionId(sessionId);

    int rowCount = session.getRowCount() != null ? session.getRowCount() : 0;
    int colCount = session.getColCount() != null ? session.getColCount() : 0;

    // 构建座位坐标索引 (rowNo, colNo) -> Seat
    Map<String, Seat> seatGrid = new HashMap<>();
    List<Long> seatIds = new ArrayList<>();
    for (Seat seat : seats) {
        seatGrid.put(seat.getRowNo() + ":" + seat.getColNo(), seat);
        seatIds.add(seat.getId());
    }

    // 批量查询 Redis 实时状态
    Map<Long, Integer> statusMap = seatIds.isEmpty()
            ? new HashMap<>()
            : inventoryService.batchGetSeatStatus(sessionId, seatIds);

    // 构建价格区域列表
    List<AreaPriceVO> areaPriceList = new ArrayList<>();
    for (SeatArea area : areas) {
        AreaPriceVO vo = new AreaPriceVO();
        vo.setAreaId(area.getAreaId());
        vo.setPrice(area.getPrice().toPlainString());
        vo.setOriginPrice(area.getOriginPrice().toPlainString());
        areaPriceList.add(vo);
    }

    // 构建网格行列
    List<SeatRowVO> seatRows = new ArrayList<>();
    for (int row = 1; row <= rowCount; row++) {
        List<SeatColVO> columns = new ArrayList<>();
        for (int col = 1; col <= colCount; col++) {
            Seat seat = seatGrid.get(row + ":" + col);
            SeatColVO colVO = new SeatColVO();
            if (seat == null) {
                // 空位占位
                colVO.setColId("");
                colVO.setColNum("");
                colVO.setSeatName(null);
                colVO.setType(0);
                colVO.setAreaId(null);
                colVO.setStatus(null);
            } else {
                colVO.setColId(String.valueOf(seat.getId()));
                colVO.setColNum(String.valueOf(seat.getColNo()));
                colVO.setSeatName(seat.getSeatName());
                colVO.setType(seat.getType());
                colVO.setAreaId(seat.getAreaId());
                colVO.setStatus(statusMap.getOrDefault(seat.getId(), 0));
            }
            columns.add(colVO);
        }
        SeatRowVO rowVO = new SeatRowVO();
        rowVO.setRowsId(String.valueOf(row));
        rowVO.setRowsNum(String.valueOf(row));
        rowVO.setColumns(columns);
        seatRows.add(rowVO);
    }

    SeatSectionVO seatSection = new SeatSectionVO();
    seatSection.setRowCount(rowCount);
    seatSection.setColumnCount(colCount);
    seatSection.setSeatRows(seatRows);

    SessionSeatResponse response = new SessionSeatResponse();
    response.setAreaPriceList(areaPriceList);
    response.setSeatSection(seatSection);
    return response;
}
```

---

### Task 10: OrderService 修改（情侣座校验 + 区域价格）

**Files:**
- Modify: `core/src/main/java/com/ticket/core/service/OrderService.java`

- [ ] **Step 1: 新增 SeatMapper 依赖注入**

构造器增加 `SeatMapper`：

```java
private final OrderMapper orderMapper;
private final OrderItemMapper orderItemMapper;
private final SeatInventoryService inventoryService;
private final PurchaseLimitService purchaseLimitService;
private final SeatMapper seatMapper;
private final SnowflakeIdGenerator snowflake = new SnowflakeIdGenerator(1, 1);

public OrderService(OrderMapper orderMapper,
                    OrderItemMapper orderItemMapper,
                    SeatInventoryService inventoryService,
                    PurchaseLimitService purchaseLimitService,
                    SeatMapper seatMapper) {
    this.orderMapper = orderMapper;
    this.orderItemMapper = orderItemMapper;
    this.inventoryService = inventoryService;
    this.purchaseLimitService = purchaseLimitService;
    this.seatMapper = seatMapper;
}
```

新增 import：

```java
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.mapper.SeatMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
```

- [ ] **Step 2: 修改 createOrder 方法，增加情侣座校验 + 区域定价**

将整个 `createOrder` 方法替换为：

```java
public Order createOrder(OrderCreateRequest request) {
    Long sessionId = request.getSessionId();
    Long userId = request.getUserId();
    List<Long> seatIds = request.getSeatIds();

    // 1. 超卖兜底
    for (Long seatId : seatIds) {
        int count = orderItemMapper.countBySeatIdAndValidOrder(seatId);
        if (count > 0) {
            for (Long id : seatIds) {
                inventoryService.releaseSeat(sessionId, id);
            }
            purchaseLimitService.decrement(sessionId, userId);
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }
    }

    // 2. 从 DB 加载座位信息，校验情侣连座完整性
    List<Seat> seatList = seatMapper.selectByIds(seatIds);
    if (seatList.size() != seatIds.size()) {
        for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
        purchaseLimitService.decrement(sessionId, userId);
        throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
    }

    Set<Long> seatIdSet = new HashSet<>(seatIds);
    for (Seat seat : seatList) {
        // type=2（情侣左）或 type=3（情侣右）必须与配对座位同时出现在本次下单中
        if ((seat.getType() == 2 || seat.getType() == 3)) {
            if (seat.getPairSeatId() == null || !seatIdSet.contains(seat.getPairSeatId())) {
                for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
                purchaseLimitService.decrement(sessionId, userId);
                throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
            }
        }
    }

    // 3. 计算总价（从 Redis 区域价格缓存取，Redis miss 则抛出异常）
    Map<Long, Seat> seatMap = seatList.stream()
            .collect(Collectors.toMap(Seat::getId, s -> s));

    List<OrderItem> items = new ArrayList<>();
    for (Long seatId : seatIds) {
        Seat seat = seatMap.get(seatId);
        String priceStr = inventoryService.getAreaPrice(sessionId, seat.getAreaId());
        if (priceStr == null) {
            for (Long id : seatIds) inventoryService.releaseSeat(sessionId, id);
            purchaseLimitService.decrement(sessionId, userId);
            throw new BusinessException(ErrorCode.SEAT_NOT_AVAILABLE);
        }
        BigDecimal price = new BigDecimal(priceStr);
        OrderItem item = new OrderItem();
        item.setOrderId(0L);
        item.setSeatId(seatId);
        item.setPrice(price);
        item.setSeatInfo(seat.getSeatName() != null ? seat.getSeatName()
                : "row:" + seat.getRowNo() + ",col:" + seat.getColNo());
        items.add(item);
    }

    // 4. 创建订单
    BigDecimal totalAmount = items.stream()
            .map(OrderItem::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Order order = new Order();
    order.setOrderNo(String.valueOf(snowflake.nextId()));
    order.setUserId(userId);
    order.setSessionId(sessionId);
    order.setTotalAmount(totalAmount);
    order.setStatus(0);
    order.setExpireTime(LocalDateTime.now().plusMinutes(5));
    order.setCreateTime(LocalDateTime.now());
    order.setUpdateTime(LocalDateTime.now());

    orderMapper.insert(order);

    for (OrderItem item : items) {
        item.setOrderId(order.getId());
    }
    orderItemMapper.batchInsert(items);

    return order;
}
```

---

### Task 11: Admin SeatController 修改

**Files:**
- Modify: `admin/src/main/java/com/ticket/admin/controller/SeatController.java`

- [ ] **Step 1: 完整替换 SeatController**

```java
// 文件：admin/src/main/java/com/ticket/admin/controller/SeatController.java
package com.ticket.admin.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import com.ticket.core.domain.entity.ShowSession;
import com.ticket.core.mapper.SeatMapper;
import com.ticket.core.service.SeatAreaService;
import com.ticket.core.service.SeatInventoryService;
import com.ticket.core.service.ShowService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/seat")
public class SeatController {

    private final SeatMapper seatMapper;
    private final SeatInventoryService inventoryService;
    private final ShowService showService;
    private final SeatAreaService seatAreaService;

    public SeatController(SeatMapper seatMapper,
                          SeatInventoryService inventoryService,
                          ShowService showService,
                          SeatAreaService seatAreaService) {
        this.seatMapper = seatMapper;
        this.inventoryService = inventoryService;
        this.showService = showService;
        this.seatAreaService = seatAreaService;
    }

    /**
     * 批量创建座位
     * seats 中每个元素需提供: rowNo, colNo, type, areaId, seatName, pairSeatId(情侣座才填)
     */
    @PostMapping("/batch")
    public Result<?> batchCreateSeats(@RequestBody BatchCreateRequest req) {
        List<Seat> seats = req.getSeats();
        LocalDateTime now = LocalDateTime.now();
        seats.forEach(s -> {
            s.setSessionId(req.getSessionId());
            s.setStatus(0);
            s.setCreateTime(now);
        });
        seatMapper.batchInsert(seats);
        return Result.success(seats);
    }

    /**
     * 查询场次座位列表
     */
    @GetMapping("/list")
    public Result<?> listSeats(@RequestParam Long sessionId) {
        return Result.success(seatMapper.selectBySessionId(sessionId));
    }

    /**
     * 保存/覆盖场次价格区域（调用前先配置好区域，再做 warmup）
     */
    @PostMapping("/area/save")
    public Result<?> saveAreas(@RequestBody SaveAreasRequest req) {
        List<SeatArea> areas = req.getAreas();
        areas.forEach(a -> a.setSessionId(req.getSessionId()));
        seatAreaService.saveAreas(req.getSessionId(), areas);
        return Result.success("价格区域保存成功");
    }

    /**
     * 查询场次价格区域列表
     */
    @GetMapping("/area/list")
    public Result<?> listAreas(@RequestParam Long sessionId) {
        return Result.success(seatAreaService.getAreasBySession(sessionId));
    }

    /**
     * 预热：将座位库存和区域价格写入 Redis，并将场次状态置为开售(status=1)
     * 先执行 /batch 录入座位、/area/save 录入区域，再调此接口
     */
    @PostMapping("/warmup/{sessionId}")
    public Result<?> warmupSeats(@PathVariable Long sessionId) {
        List<Seat> seats = seatMapper.selectBySessionId(sessionId);
        if (seats == null || seats.isEmpty()) {
            return Result.fail(400, "该场次暂无座位数据");
        }
        List<SeatArea> areas = seatAreaService.getAreasBySession(sessionId);
        if (areas == null || areas.isEmpty()) {
            return Result.fail(400, "该场次暂无价格区域数据，请先调用 /area/save");
        }
        inventoryService.warmup(sessionId, seats, areas);

        ShowSession session = showService.getSession(sessionId);
        if (session != null) {
            session.setStatus(1);
            showService.updateSession(session);
        }
        return Result.success("预热完成，共 " + seats.size() + " 个座位，" + areas.size() + " 个价格区域");
    }

    @Data
    public static class BatchCreateRequest {
        private Long sessionId;
        private List<Seat> seats;
    }

    @Data
    public static class SaveAreasRequest {
        private Long sessionId;
        private List<SeatArea> areas;
    }
}
```

---

### Task 12: User ShowController 修改

**Files:**
- Modify: `user/src/main/java/com/ticket/user/controller/ShowController.java`

- [ ] **Step 1: 完整替换 ShowController**

```java
// 文件：user/src/main/java/com/ticket/user/controller/ShowController.java
package com.ticket.user.controller;

import com.ticket.common.result.Result;
import com.ticket.core.domain.vo.SessionSeatResponse;
import com.ticket.core.service.ShowService;
import com.ticket.user.config.NoLogin;
import org.springframework.web.bind.annotation.*;

@NoLogin
@RestController
@RequestMapping("/api/show")
public class ShowController {

    private final ShowService showService;

    public ShowController(ShowService showService) {
        this.showService = showService;
    }

    @GetMapping("/list")
    public Result<?> listShows() {
        return Result.success(showService.listShows(1));
    }

    @GetMapping("/{id}")
    public Result<?> getShow(@PathVariable Long id) {
        return Result.success(showService.getShow(id));
    }

    @GetMapping("/{id}/sessions")
    public Result<?> listSessions(@PathVariable Long id) {
        return Result.success(showService.listSessions(id));
    }

    /**
     * 获取场次座位图（含价格区域列表 + 网格化座位信息）
     */
    @GetMapping("/session/{sessionId}/seats")
    public Result<SessionSeatResponse> getSessionSeats(@PathVariable Long sessionId) {
        return Result.success(showService.getSeatSection(sessionId));
    }
}
```

---

## 自查

### Spec 覆盖检查
- [x] `areaPriceList` 返回价格区域 → Task 6 VO + Task 9 getSeatSection
- [x] `seatSection` 网格化结构 → Task 6 VO + Task 9 getSeatSection
- [x] type=0 空位占位（不入库）→ Task 9 getSeatSection 按 rowCount×colCount 填充
- [x] type=1 普通座 / type=2 情侣左 / type=3 情侣右 → Task 2 Seat 实体
- [x] 情侣连座配对字段 pairSeatId → Task 1 Schema + Task 2 实体
- [x] area_id 统一 String → 贯穿 Task 1-12
- [x] 座位返回 status（0/1/2）→ Task 7 batchGetSeatStatus + Task 6 SeatColVO
- [x] Redis warmup 改存 type/areaId → Task 7
- [x] 下单价格从区域取 → Task 10
- [x] 情侣连座下单原子锁（Lua 脚本不变，应用层校验）→ Task 10
- [x] Admin 新增区域管理接口 → Task 11
- [x] Admin 批量创建座位接受新字段 → Task 11

### 类型一致性检查
- `SeatArea.areaId` 为 `String`，`Seat.areaId` 为 `String`，`AreaPriceVO.areaId` 为 `String` ✓
- `SeatColVO.colId` 为 `String`，值为 `seat.getId().toString()` ✓
- `inventoryService.warmup(sessionId, seats, areas)` 签名在 Task 7 定义，Task 11 调用 ✓
- `showService.getSeatSection(sessionId)` 在 Task 9 定义，Task 12 调用 ✓
- `seatMapper.selectByIds(seatIds)` 在 Task 4 定义，Task 10 调用 ✓
