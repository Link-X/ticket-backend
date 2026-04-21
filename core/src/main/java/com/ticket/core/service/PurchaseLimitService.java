package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 购买限制服务 — 基于 Redis 实现用户在单个场次的购票数量限制
 */
@Service
public class PurchaseLimitService {

    /**
     * 原子性检查并递增购买数量 Lua 脚本：
     * 读取当前购买数量，如果已达到限额则返回 0，否则递增计数并返回 1
     */
    private static final DefaultRedisScript<Long> CHECK_AND_INCR_SCRIPT;

    /**
     * 安全递减购买数量 Lua 脚本：
     * 检查当前值，如果不存在或值 <= 0 则返回 0，否则递减计数并返回新值
     */
    private static final DefaultRedisScript<Long> DECR_SAFE_SCRIPT;

    static {
        CHECK_AND_INCR_SCRIPT = new DefaultRedisScript<>();
        CHECK_AND_INCR_SCRIPT.setResultType(Long.class);
        CHECK_AND_INCR_SCRIPT.setScriptText(
            "local count = redis.call('GET', KEYS[1])\n" +
            "if count == false then count = 0 else count = tonumber(count) end\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "if count >= limit then return 0 end\n" +
            "redis.call('INCR', KEYS[1])\n" +
            "if count == 0 then\n" +
            "  redis.call('EXPIRE', KEYS[1], 604800)\n" +
            "end\n" +
            "return 1"
        );

        DECR_SAFE_SCRIPT = new DefaultRedisScript<>();
        DECR_SAFE_SCRIPT.setScriptText(
            "local v = redis.call('GET', KEYS[1])\n" +
            "if v == false or tonumber(v) <= 0 then return 0 end\n" +
            "return redis.call('DECR', KEYS[1])"
        );
        DECR_SAFE_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;

    public PurchaseLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子性检查并递增购买数量（如果未达到限额）
     *
     * @param sessionId 场次 ID
     * @param userId    用户 ID
     * @param limit     购票限额
     * @return 如果成功递增（未达到限额）返回 true，否则返回 false
     */
    public boolean checkAndIncrement(long sessionId, long userId, int limit) {
        String key = RedisKeys.sessionPurchase(sessionId, userId);
        Long result = redisTemplate.execute(
                CHECK_AND_INCR_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(limit)
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 递减购买数量（可能的退票场景）
     * 使用 Lua 脚本保证计数不低于 0
     *
     * @param sessionId 场次 ID
     * @param userId    用户 ID
     */
    public void decrement(long sessionId, long userId) {
        String key = RedisKeys.sessionPurchase(sessionId, userId);
        redisTemplate.execute(DECR_SAFE_SCRIPT, Collections.singletonList(key));
    }

    /**
     * 获取用户在该场次的当前购买数量
     *
     * @param sessionId 场次 ID
     * @param userId    用户 ID
     * @return 购买数量（key 不存在返回 0）
     */
    public int getPurchaseCount(long sessionId, long userId) {
        String key = RedisKeys.sessionPurchase(sessionId, userId);
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
