package com.ticket.common.service;

import com.ticket.common.constant.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class BlacklistService {

    private final StringRedisTemplate redisTemplate;

    public BlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isUserBlacklisted(long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.blacklistUser(userId)));
    }

    public boolean isIpBlacklisted(String ip) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.blacklistIp(ip)));
    }

    public void addUserToBlacklist(long userId, Duration ttl) {
        redisTemplate.opsForValue().set(RedisKeys.blacklistUser(userId), "1", ttl);
    }

    public void addIpToBlacklist(String ip, Duration ttl) {
        redisTemplate.opsForValue().set(RedisKeys.blacklistIp(ip), "1", ttl);
    }

    public void removeUserFromBlacklist(long userId) {
        redisTemplate.delete(RedisKeys.blacklistUser(userId));
    }

    public void removeIpFromBlacklist(String ip) {
        redisTemplate.delete(RedisKeys.blacklistIp(ip));
    }
}
