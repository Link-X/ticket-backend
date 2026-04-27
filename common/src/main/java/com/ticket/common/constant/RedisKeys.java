package com.ticket.common.constant;

/**
 * Redis Key 常量工具类.
 *
 * 统一管理所有 Redis 操作的 key 格式,避免散落在各处硬编码.
 * 每个方法对应一种 key 模式:
 *   sessionSeats      — Set,存储场次下所有可售座位 ID
 *   seatInfo          — Hash,存储座位详细信息(排号/列号/价格等)
 *   seatLock          — String,座位锁(NX+EXPIRE 实现分布式锁)
 *   sessionPurchase   — String,用户在某场次的已购数量
 *   ticketOrderQueue  — Redis Stream,订单削峰队列
 *   userRateLimit     — String,用户限流计数
 *   sessionLock       — String,场次级分布式锁
 */
public class RedisKeys {

    private RedisKeys() {}

    public static String sessionSeats(long sessionId) {
        return "session:seats:" + sessionId;
    }

    public static String seatInfo(long seatId) {
        return "seat:info:" + seatId;
    }

    public static String seatLock(long sessionId, long seatId) {
        return "seat:lock:" + sessionId + ":" + seatId;
    }

    public static String sessionPurchase(long sessionId, long userId) {
        return "session:purchase:" + sessionId + ":" + userId;
    }

    public static String ticketOrderQueue(long sessionId) {
        return "ticket:order:queue:" + sessionId;
    }

    public static String userRateLimit(long userId) {
        return "user:rate:limit:" + userId;
    }

    public static String sessionLock(long sessionId) {
        return "session:lock:" + sessionId;
    }

    /** 场次内某区域的价格信息 Hash（字段: price, originPrice） */
    public static String seatAreaPrice(long sessionId, String areaId) {
        return "session:area:price:" + sessionId + ":" + areaId;
    }

    /** requestId（Redis Stream record ID）到订单号的映射 */
    public static String orderRequest(String requestId) {
        return "order:request:" + requestId;
    }

    /** 场次抢票限流计数（固定窗口，key 含秒级时间戳） */
    public static String submitRateLimit(long sessionId, long windowSecond) {
        return "rate:submit:" + sessionId + ":" + windowSecond;
    }

    public static String rateLimitGlobal(String methodKey, long windowSecond) {
        return "rate:global:" + methodKey + ":" + windowSecond;
    }

    public static String rateLimitUser(long userId, String methodKey, long windowSecond) {
        return "rate:user:" + userId + ":" + methodKey + ":" + windowSecond;
    }

    public static String rateLimitIp(String ip, String methodKey, long windowSecond) {
        return "rate:ip:" + ip + ":" + methodKey + ":" + windowSecond;
    }

    public static String blacklistUser(long userId) {
        return "blacklist:user:" + userId;
    }

    public static String blacklistIp(String ip) {
        return "blacklist:ip:" + ip;
    }
}
