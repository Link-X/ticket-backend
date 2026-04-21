package com.ticket.common.util;

import java.security.SecureRandom;

/**
 * 票号生成器.
 *
 * 生成格式为 "TK" + 6 位随机字母数字的票号(共 8 位).
 * 排除易混淆字符 O/0/I/1,使用 SecureRandom 保证随机性.
 * 业务层需在循环中检查唯一性,确保不重复.
 */
public class TicketNoGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        StringBuilder sb = new StringBuilder(9);
        sb.append("TK");
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
