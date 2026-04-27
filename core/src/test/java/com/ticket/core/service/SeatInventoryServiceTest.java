package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import com.ticket.core.domain.entity.Seat;
import com.ticket.core.domain.entity.SeatArea;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    private static final long SESSION_ID = 100L;
    private static final String USER_ID = "user-001";
    private static final long TTL = 300L;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                redis.getHost(),
                redis.getMappedPort(6379)
        );
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.afterPropertiesSet();

        redisTemplate.getConnectionFactory().getConnection().flushDb();

        seatInventoryService = new SeatInventoryService(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate.getConnectionFactory() instanceof LettuceConnectionFactory) {
            ((LettuceConnectionFactory) redisTemplate.getConnectionFactory()).destroy();
        }
    }

    /** 构建普通座位（type=1，areaId="0"） */
    private Seat buildSeat(long id, int rowNo, int colNo) {
        Seat seat = new Seat();
        seat.setId(id);
        seat.setSessionId(SESSION_ID);
        seat.setRowNo(rowNo);
        seat.setColNo(colNo);
        seat.setType(1);
        seat.setAreaId("0");
        seat.setSeatName(rowNo + "排0" + colNo + "座");
        return seat;
    }

    /** 构建价格区域 */
    private SeatArea buildArea(String areaId, String price, String originPrice) {
        SeatArea area = new SeatArea();
        area.setSessionId(SESSION_ID);
        area.setAreaId(areaId);
        area.setPrice(new BigDecimal(price));
        area.setOriginPrice(new BigDecimal(originPrice));
        return area;
    }

    /**
     * 测试1：warmup 后 getAvailableCount 返回正确数量
     */
    @Test
    void warmup_shouldAddSeatsToSet() {
        List<Seat> seats = Arrays.asList(
                buildSeat(1L, 1, 1),
                buildSeat(2L, 1, 2),
                buildSeat(3L, 1, 3)
        );
        List<SeatArea> areas = Collections.singletonList(buildArea("0", "680.00", "800.00"));

        seatInventoryService.warmup(SESSION_ID, seats, areas);

        Long count = seatInventoryService.getAvailableCount(SESSION_ID);
        assertEquals(3L, count, "warmup 后可售座位数量应为 3");

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
        boolean firstLock = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(firstLock, "首次锁定应成功");

        boolean secondLock = seatInventoryService.lockSeat(SESSION_ID, 1L, "user-002", TTL);
        assertFalse(secondLock, "已被锁定的座位再次锁定应返回 false");
    }

    /**
     * 测试4：batchLockSeats 原子性 — 某个座位已被锁时整批失败，已锁的座位被回滚后可再次锁定
     */
    @Test
    void batchLockSeats_shouldBeAtomic() {
        boolean preLock = seatInventoryService.lockSeat(SESSION_ID, 2L, "user-999", TTL);
        assertTrue(preLock, "预先锁定座位 2 应成功");

        List<Long> seatIds = Arrays.asList(1L, 2L, 3L);
        boolean batchResult = seatInventoryService.batchLockSeats(SESSION_ID, seatIds, USER_ID, TTL);
        assertFalse(batchResult, "批量锁定时若某座位已被锁，整批应失败");

        String lockKey1 = RedisKeys.seatLock(SESSION_ID, 1L);
        String lockKey3 = RedisKeys.seatLock(SESSION_ID, 3L);
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey1)), "失败后座位 1 的锁应已回滚");
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey3)), "失败后座位 3 的锁应已回滚");

        boolean retryLock1 = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(retryLock1, "批量失败回滚后，座位 1 应可以重新锁定");
    }

    /**
     * 测试5：releaseSeat 后应删除锁并将座位重新加入可售集合，可再次 lockSeat
     */
    @Test
    void releaseSeat_shouldRemoveLockAndAddBackToSet() {
        List<Seat> seats = Collections.singletonList(buildSeat(1L, 1, 1));
        List<SeatArea> areas = Collections.singletonList(buildArea("0", "680.00", "800.00"));
        seatInventoryService.warmup(SESSION_ID, seats, areas);

        boolean locked = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(locked, "锁定座位应成功");

        String lockKey = RedisKeys.seatLock(SESSION_ID, 1L);
        assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey)), "锁定后锁 key 应存在");

        seatInventoryService.releaseSeat(SESSION_ID, 1L);

        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(lockKey)), "释放后锁 key 应被删除");

        Set<String> availableIds = seatInventoryService.getAvailableSeatIds(SESSION_ID);
        assertTrue(availableIds.contains("1"), "释放后座位应重新出现在可售集合中");

        boolean relockResult = seatInventoryService.lockSeat(SESSION_ID, 1L, USER_ID, TTL);
        assertTrue(relockResult, "释放后应可以再次锁定座位");
    }

    /**
     * 测试6：batchGetSeatStatus 正确返回三种状态
     * 可售(0)、已锁(1)、已售(2)
     */
    @Test
    void batchGetSeatStatus_shouldReturnCorrectStatus() {
        List<Seat> seats = Arrays.asList(
                buildSeat(1L, 1, 1),
                buildSeat(2L, 1, 2),
                buildSeat(3L, 1, 3)
        );
        List<SeatArea> areas = Collections.singletonList(buildArea("0", "680.00", "800.00"));
        seatInventoryService.warmup(SESSION_ID, seats, areas);

        // warmup 后全部可售
        Map<Long, Integer> status = seatInventoryService.batchGetSeatStatus(SESSION_ID, Arrays.asList(1L, 2L, 3L));
        assertEquals(0, status.get(1L), "座位 1 应为可售(0)");
        assertEquals(0, status.get(2L), "座位 2 应为可售(0)");
        assertEquals(0, status.get(3L), "座位 3 应为可售(0)");

        // 锁定座位 2 → 状态变为已锁(1)
        seatInventoryService.lockSeat(SESSION_ID, 2L, USER_ID, TTL);
        status = seatInventoryService.batchGetSeatStatus(SESSION_ID, Arrays.asList(1L, 2L, 3L));
        assertEquals(0, status.get(1L), "座位 1 仍应为可售(0)");
        assertEquals(1, status.get(2L), "座位 2 锁定后应为已锁(1)");
        assertEquals(0, status.get(3L), "座位 3 仍应为可售(0)");

        // 消费座位 2（支付成功）→ 状态变为已售(2)
        seatInventoryService.consumeSeat(SESSION_ID, 2L, USER_ID);
        status = seatInventoryService.batchGetSeatStatus(SESSION_ID, Arrays.asList(1L, 2L, 3L));
        assertEquals(2, status.get(2L), "支付后座位 2 应为已售(2)");
    }

    /**
     * 测试7：getAreaPrice 在 warmup 后能正确返回区域价格
     */
    @Test
    void getAreaPrice_shouldReturnPriceAfterWarmup() {
        List<Seat> seats = Collections.singletonList(buildSeat(1L, 1, 1));
        List<SeatArea> areas = Arrays.asList(
                buildArea("0", "680.00", "800.00"),
                buildArea("1", "1280.00", "1500.00")
        );
        seatInventoryService.warmup(SESSION_ID, seats, areas);

        String price0 = seatInventoryService.getAreaPrice(SESSION_ID, "0");
        assertEquals("680.00", price0, "区域 0 的价格应为 680.00");

        String price1 = seatInventoryService.getAreaPrice(SESSION_ID, "1");
        assertEquals("1280.00", price1, "区域 1 的价格应为 1280.00");

        String priceNull = seatInventoryService.getAreaPrice(SESSION_ID, "99");
        assertNull(priceNull, "不存在的区域应返回 null");
    }
}
