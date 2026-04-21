package com.ticket.common.constant;

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
}
