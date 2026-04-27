package com.ticket.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class RateLimitService {

    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
        RATE_LIMIT_SCRIPT.setScriptText(
            "local current = redis.call('INCR', KEYS[1])\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', KEYS[1], ARGV[2])\n" +
            "end\n" +
            "if current > tonumber(ARGV[1]) then\n" +
            "    return 0\n" +
            "end\n" +
            "return 1"
        );
    }

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 固定窗口限流。
     *
     * @param key           Redis key（由调用方拼好）
     * @param limit         窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     * @return true=放行，false=触发限流
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        Long result = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(windowSeconds + 1)
        );
        return Long.valueOf(1L).equals(result);
    }
}
