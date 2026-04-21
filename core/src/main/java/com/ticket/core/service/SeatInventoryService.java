package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import com.ticket.core.domain.entity.Seat;
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
    }

    private final StringRedisTemplate redisTemplate;

    public SeatInventoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 预热座位库存：Pipeline 批量写入座位集合及各座位 Hash 信息
     *
     * @param sessionId 场次 ID
     * @param seats     需要写入的座位列表
     */
    public void warmup(long sessionId, List<Seat> seats) {
        String sessionKey = RedisKeys.sessionSeats(sessionId);

        redisTemplate.executePipelined((RedisConnection connection) -> {
            // 批量 SADD 所有 seatId 到场次座位集合（使用底层 Pipeline 通道）
            byte[] sessionKeyBytes = sessionKey.getBytes(StandardCharsets.UTF_8);
            for (Seat seat : seats) {
                connection.sAdd(sessionKeyBytes,
                        String.valueOf(seat.getId()).getBytes(StandardCharsets.UTF_8));
            }
            connection.expire(sessionKeyBytes, INVENTORY_TTL_SECONDS);

            // 为每个座位写入 Hash 信息
            for (Seat seat : seats) {
                String seatInfoKey = RedisKeys.seatInfo(seat.getId());
                byte[] seatInfoKeyBytes = seatInfoKey.getBytes(StandardCharsets.UTF_8);

                Map<byte[], byte[]> seatInfoMap = new HashMap<>();
                seatInfoMap.put("row".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(seat.getRowNo()).getBytes(StandardCharsets.UTF_8));
                seatInfoMap.put("col".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(seat.getColNo()).getBytes(StandardCharsets.UTF_8));
                seatInfoMap.put("type".getBytes(StandardCharsets.UTF_8),
                        (seat.getSeatType() != null ? seat.getSeatType() : "").getBytes(StandardCharsets.UTF_8));
                seatInfoMap.put("price".getBytes(StandardCharsets.UTF_8),
                        (seat.getPrice() != null ? seat.getPrice().toPlainString() : "0").getBytes(StandardCharsets.UTF_8));

                connection.hMSet(seatInfoKeyBytes, seatInfoMap);
                connection.expire(seatInfoKeyBytes, INVENTORY_TTL_SECONDS);
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
     * 消费座位（支付成功后调用）：从可售集合移除 + 删除锁 key
     *
     * @param sessionId 场次 ID
     * @param seatId    座位 ID
     */
    public void consumeSeat(long sessionId, long seatId) {
        String sessionKey = RedisKeys.sessionSeats(sessionId);
        String lockKey = RedisKeys.seatLock(sessionId, seatId);

        // 从可售集合中移除
        redisTemplate.opsForSet().remove(sessionKey, String.valueOf(seatId));
        // 删除锁 key
        redisTemplate.delete(lockKey);
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
}
