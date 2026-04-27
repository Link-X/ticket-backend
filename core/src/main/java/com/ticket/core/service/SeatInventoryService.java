package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 座位库存服务 — 基于 Redis 实现座位池管理、锁定与释放
 */
@Service
public class SeatInventoryService {

    /** 座位库存默认过期时间：7 天（秒） */
    private static final long INVENTORY_TTL_SECONDS = 7 * 24 * 3600L;

    /**
     * 批量原子锁座 Lua 脚本：
     * 遍历所有 seatId，依次执行 SETNX；任意一个已被锁定则回滚已锁定的 key 后返回 0，全部成功返回 1。
     */
    private static final DefaultRedisScript<Long> BATCH_LOCK_SCRIPT;

    /**
     * 原子释放座位 Lua 脚本：
     * 删除锁 key 并将 seatId 重新加入可售集合，两步操作原子化。
     */
    private static final DefaultRedisScript<Long> RELEASE_SEAT_SCRIPT;

    /**
     * 原子消费座位 Lua脚本:
     * 验证锁的持有者后，原子执行：从可售集合移除 + 删除锁
     */
    private static final DefaultRedisScript<Long> CONSUME_SEAT_SCRIPT;

    static {
        BATCH_LOCK_SCRIPT = new DefaultRedisScript<>();
        BATCH_LOCK_SCRIPT.setResultType(Long.class);
        BATCH_LOCK_SCRIPT.setScriptText(
            "local sessionId = ARGV[1]\n" +
            "local userId = ARGV[2]\n" +
            "local ttl = ARGV[3]\n" +
            "local lockedKeys = {}\n" +
            "for i = 4, #ARGV do\n" +
            "  local seatId = ARGV[i]\n" +
            "  local lockKey = 'seat:lock:' .. sessionId .. ':' .. seatId\n" +
            "  local ok = redis.call('SETNX', lockKey, userId)\n" +
            "  if ok == 0 then\n" +
            "    for _, k in ipairs(lockedKeys) do\n" +
            "      redis.call('DEL', k)\n" +
            "    end\n" +
            "    return 0\n" +
            "  end\n" +
            "  redis.call('EXPIRE', lockKey, ttl)\n" +
            "  table.insert(lockedKeys, lockKey)\n" +
            "end\n" +
            "return 1"
        );

        RELEASE_SEAT_SCRIPT = new DefaultRedisScript<>();
        RELEASE_SEAT_SCRIPT.setResultType(Long.class);
        RELEASE_SEAT_SCRIPT.setScriptText(
            "redis.call('DEL', KEYS[1])\n" +
            "redis.call('SADD', KEYS[2], ARGV[1])\n" +
            "return 1"
        );

        CONSUME_SEAT_SCRIPT = new DefaultRedisScript<>();
        CONSUME_SEAT_SCRIPT.setResultType(Long.class);
        CONSUME_SEAT_SCRIPT.setScriptText(
                "local sessionKey = KEYS[1]\n" +
                        "local lockKey = KEYS[2]\n" +
                        "local seatId = ARGV[1]\n" +
                        "local userId = ARGV[2]\n" +
                        "\n" +
                        "-- 检查锁是否存在且属于当前用户\n" +
                        "local lockOwner = redis.call('GET', lockKey)\n" +
                        "if not lockOwner then\n" +
                        "    -- 锁不存在，可能已被释放，返回 0\n" +
                        "    return 0\n" +
                        "end\n" +
                        "if lockOwner ~= userId then\n" +
                        "    -- 锁不属于当前用户，返回 0\n" +
                        "    return 0\n" +
                        "end\n" +
                        "\n" +
                        "-- 原子执行：从可售集合移除 + 删除锁\n" +
                        "redis.call('SREM', sessionKey, seatId)\n" +
                        "redis.call('DEL', lockKey)\n" +
                        "return 1\n"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public SeatInventoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 预热座位库存：Pipeline 批量写入座位集合及各座位 Hash 信息，同时缓存区域价格
     *
     * @param sessionId 场次 ID
     * @param seats     需要写入的座位列表
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

    /**
     * 获取场次当前可售座位 ID 集合
     *
     * @param sessionId 场次 ID
     * @return 可售座位 ID 字符串集合
     */
    public Set<String> getAvailableSeatIds(long sessionId) {
        String sessionKey = RedisKeys.sessionSeats(sessionId);
        return redisTemplate.opsForSet().members(sessionKey);
    }

    /**
     * 获取场次当前可售座位数量
     *
     * @param sessionId 场次 ID
     * @return 可售座位数量
     */
    public Long getAvailableCount(long sessionId) {
        String sessionKey = RedisKeys.sessionSeats(sessionId);
        return redisTemplate.opsForSet().size(sessionKey);
    }

    /**
     * 单个座位加锁（SETNX + TTL）
     *
     * @param sessionId  场次 ID
     * @param seatId     座位 ID
     * @param userId     锁定者用户 ID
     * @param ttlSeconds 锁过期时间（秒）
     * @return 加锁成功返回 true，座位已被锁定返回 false
     */
    public boolean lockSeat(long sessionId, long seatId, String userId, long ttlSeconds) {
        String lockKey = RedisKeys.seatLock(sessionId, seatId);
        Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, userId, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 批量原子锁座：通过 Lua 脚本保证原子性，任一座位已被锁定则全部失败
     *
     * @param sessionId  场次 ID
     * @param seatIds    待锁定座位 ID 列表
     * @param userId     锁定者用户 ID
     * @param ttlSeconds 锁过期时间（秒）
     * @return 全部锁定成功返回 true，否则返回 false
     */
    public boolean batchLockSeats(long sessionId, List<Long> seatIds, String userId, long ttlSeconds) {
        // KEYS 为空列表，ARGV = [sessionId, userId, ttlSeconds, seatId1, seatId2, ...]
        List<String> argv = new ArrayList<>();
        argv.add(String.valueOf(sessionId));
        argv.add(userId);
        argv.add(String.valueOf(ttlSeconds));
        for (Long seatId : seatIds) {
            argv.add(String.valueOf(seatId));
        }

        Long result = redisTemplate.execute(
                BATCH_LOCK_SCRIPT,
                Collections.emptyList(),
                argv.toArray(new String[0])
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 释放座位锁并将座位重新加入可售集合（超时未支付场景）
     *
     * @param sessionId 场次 ID
     * @param seatId    座位 ID
     */
    public void releaseSeat(long sessionId, long seatId) {
        String lockKey = RedisKeys.seatLock(sessionId, seatId);
        String sessionKey = RedisKeys.sessionSeats(sessionId);

        // 使用 Lua 脚本原子化执行：删除锁 + 重新加入可售集合，消除竞态窗口
        redisTemplate.execute(
                RELEASE_SEAT_SCRIPT,
                Arrays.asList(lockKey, sessionKey),
                String.valueOf(seatId)
        );
    }

    /**
     * 消费座位（支付成功后调用）
     * 原子操作：验证锁归属 + 从可售集合移除 + 删除锁
     *
     * @param sessionId 场次 ID
     * @param seatId    座位 ID
     * @param userId    用户 ID（必须是锁的持有者）
     * @return 消费成功返回 true，失败返回 false
     */
    public boolean consumeSeat(long sessionId, long seatId, String userId) {
        String sessionKey = RedisKeys.sessionSeats(sessionId);
        String lockKey = RedisKeys.seatLock(sessionId, seatId);

        Long result = redisTemplate.execute(
                CONSUME_SEAT_SCRIPT,
                Arrays.asList(sessionKey, lockKey),
                String.valueOf(seatId), userId
        );
        if (Long.valueOf(1L).equals(result)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 获取座位详细信息（row/col/type/price）
     *
     * @param seatId 座位 ID
     * @return 座位信息 Map，字段：row、col、type、price
     */
    public Map<Object, Object> getSeatInfo(long seatId) {
        String seatInfoKey = RedisKeys.seatInfo(seatId);
        return redisTemplate.opsForHash().entries(seatInfoKey);
    }

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

        // 找出在可售集合中的座位，需要进一步检查是否被锁
        List<Long> inAvailable = new ArrayList<>();
        for (Long seatId : seatIds) {
            if (availableSet != null && availableSet.contains(String.valueOf(seatId))) {
                inAvailable.add(seatId);
            }
        }

        // Pipeline 批量 EXISTS 检查锁 key
        List<Object> existsResults = redisTemplate.executePipelined((RedisConnection connection) -> {
            for (Long seatId : inAvailable) {
                String lockKey = RedisKeys.seatLock(sessionId, seatId);
                connection.exists(lockKey.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });

        // 组装锁状态 Map
        Map<Long, Boolean> lockedMap = new HashMap<>();
        for (int i = 0; i < inAvailable.size(); i++) {
            Object result = existsResults.get(i);
            boolean locked = Boolean.TRUE.equals(result) || (result instanceof Long && (Long) result > 0);
            lockedMap.put(inAvailable.get(i), locked);
        }

        // 组装最终状态 Map
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
}
