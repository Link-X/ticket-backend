package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import com.ticket.core.domain.entity.Seat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SeatInventoryService 单元测试
 * 使用 Testcontainers 启动真实 Redis，验证座位库存管理逻辑
 */
@Testcontainers
class SeatInventoryServiceTest {

    /** 启动 Redis 7 Alpine 容器，共享整个测试类生命周期 */
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private StringRedisTemplate redisTemplate;
    private SeatInventoryService seatInventoryService;

    /** 测试用场次 ID */
    private static final long SESSION_ID = 100L;
    /** 测试用用户 ID */
    private static final String USER_ID = "user-001";
    /** 锁过期时间（秒） */
    private static final long TTL = 300L;

    @BeforeEach
    void setUp() {
        // 创建 Lettuce 连接工厂，连接 Testcontainers 启动的 Redis
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                redis.getHost(),
                redis.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        // 每个测试前清空 Redis，保证测试独立性
        redisTemplate.getConnectionFactory().getConnection().flushDb();

        seatInventoryService = new SeatInventoryService(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        // 清理连接工厂资源（Java 11 兼容写法）
        if (redisTemplate.getConnectionFactory() instanceof LettuceConnectionFactory) {
            ((LettuceConnectionFactory) redisTemplate.getConnectionFactory()).destroy();
        }
    }

    /**
     * 构建测试用 Seat 实体
     */
    private Seat buildSeat(long id, int rowNo, int colNo) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setSessionId(SESSION_ID);
        seat.setRowNo(rowNo);
        seat.setColNo(colNo);
        seat.setSeatType("STANDARD");
        seat.setPrice(new BigDecimal("100.00"));
        return seat;
    }

    /**
     * 测试1：warmup 后 getAvailableCount 返回正确数量
     */
    @Test
    void warmup_shouldAddSeatsToSet() {
        // 准备 3 个座位
        List<Seat> seats = Arrays.asList(
                buildSeat(1L, 1, 1),
                buildSeat(2L, 1, 2),
                buildSeat(3L, 1, 3)
        );

        // 执行 warmup
        seatInventoryService.warmup(SESSION_ID, seats);

        // 验证可售数量等于 3
        Long count = seatInventoryService.getAvailableCount(SESSION_ID);
        assertEquals(3L, count, "warmup 后可售座位数量应为 3");

        // 验证可售座位 ID 集合包含所有写入的座位
        Set<String> availableIds = seatInventoryService.getAvailableSeatIds(SESSION_ID);
        assertTrue(availableIds.contains("1"), "可售集合应包含座位 ID 1");
        assertTrue(availableIds.contains("2"), "可售集合应包含座位 ID 2");
        assertTrue(availableIds.contains("3"), "可售集合应包含座位 ID 3");
    }

    /**
     * 测试2：lockSeat 在座位未被锁定时应返回 true
     */
    @Test
    void lockSeat_shouldSucceedWhenAvailable() {
        boolean result = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(result, "首次锁定未被锁定的座位应返回 true");
    }

    /**
     * 测试3：同一座位第二次 lockSeat 应返回 false
     */
    @Test
    void lockSeat_shouldFailWhenAlreadyLocked() {
        // 第一次加锁成功
        boolean firstLock = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(firstLock, "首次锁定应成功");

        // 第二次加锁同一座位，应失败
        boolean secondLock = seatInventoryService.lockSeat(SESSION_ID, 1L, "user-002", TTL);
        assertFalse(secondLock, "已被锁定的座位再次锁定应返回 false");
    }

    /**
     * 测试4：batchLockSeats 原子性 — 某个座位已被锁时整批失败，已锁的座位被回滚后可再次锁定
     */
    @Test
    void batchLockSeats_shouldBeAtomic() {
        // 预先锁定座位 2（模拟已被占用）
        boolean preLock = seatInventoryService.lockSeat(SESSION_ID, 2L, "user-999", TTL);
        assertTrue(preLock, "预先锁定座位 2 应成功");

        // 批量锁定 [1, 2, 3]，其中座位 2 已被锁 -> 整批应失败
        List<Long> seatIds = Arrays.asList(1L, 2L, 3L);
        boolean batchResult = seatInventoryService.batchLockSeats(SESSION_ID, seatIds, USER_ID, TTL);
        assertFalse(batchResult, "批量锁定时若某座位已被锁，整批应失败");

        // 验证回滚正确：座位 1 和 3 不应存在锁（已被 Lua 脚本回滚）
        String lockKey1 = RedisKeys.seatLock(SESSION_ID, 1L);
        String lockKey3 = RedisKeys.seatLock(SESSION_ID, 3L);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey1)), "失败后座位 1 的锁应已回滚");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey3)), "失败后座位 3 的锁应已回滚");

        // 验证被回滚的座位 1 可以重新被锁定
        boolean retryLock1 = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(retryLock1, "批量失败回滚后，座位 1 应可以重新锁定");
    }

    /**
     * 测试5：releaseSeat 后应删除锁并将座位重新加入可售集合，可再次 lockSeat
     */
    @Test
    void releaseSeat_shouldRemoveLockAndAddBackToSet() {
        // 先 warmup 初始化座位集合
        List<Seat> seats = Arrays.asList(buildSeat(1L, 1, 1));
        seatInventoryService.warmup(SESSION_ID, seats);

        // 锁定座位 1
        boolean locked = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(locked, "锁定座位应成功");

        // 验证锁 key 存在
        String lockKey = RedisKeys.seatLock(SESSION_ID, 1L);
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey)), "锁定后锁 key 应存在");

        // 释放座位
        seatInventoryService.releaseSeat(SESSION_ID, 1L);

        // 验证锁 key 已删除
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey)), "释放后锁 key 应被删除");

        // 验证座位重新加入可售集合
        Set<String> availableIds = seatInventoryService.getAvailableSeatIds(SESSION_ID);
        assertTrue(availableIds.contains("1"), "释放后座位应重新出现在可售集合中");

        // 验证可以再次锁定
        boolean relockResult = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(relockResult, "释放后应可以再次锁定座位");
    }
}
