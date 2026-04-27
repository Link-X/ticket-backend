package com.ticket.core.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.constant.RedisKeys;
import com.ticket.core.domain.dto.OrderCreateRequest;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.service.OrderService;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 票务订单消费者 — 从 Redis Stream 中消费订单创建消息并异步处理
 */
@Slf4j
@Service
public class TicketOrderConsumer {

    private final StringRedisTemplate redisTemplate;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Value("${ticket.stream.key:ticket:order:stream}")
    private String streamKey;

    @Value("${ticket.stream.group:order-group}")
    private String groupName;

    /** 消费者实例名称 */
    private static final String CONSUMER_NAME = "consumer-1";

    /** 控制消费循环是否继续运行 */
    private volatile boolean running = true;

    /** 后台消费线程 */
    private Thread consumerThread;

    public TicketOrderConsumer(StringRedisTemplate redisTemplate,
                               OrderService orderService,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化：创建消费组并启动后台消费线程
     */
    @PostConstruct
    public void startConsumer() {
        // 创建消费组（BUSYGROUP 表示消费组已存在，忽略该异常）
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
        } catch (Exception e) {
            // BUSYGROUP 表示消费组已存在，忽略
            log.debug("消费组已存在或创建失败，忽略: {}", e.getMessage());
        }

        // 启动 Daemon 线程进行消息消费
        consumerThread = new Thread(this::readMessages, "ticket-order-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        log.info("订单消费者已启动，streamKey={}, group={}, consumer={}", streamKey, groupName, CONSUMER_NAME);
    }

    /**
     * 持续从 Redis Stream 读取消息并处理
     */
    private void readMessages() {
        while (running) {
            try {
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                        Consumer.from(groupName, CONSUMER_NAME),
                        StreamReadOptions.empty().count(10).block(Duration.ofMillis(2000)),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                );
                if (messages != null) {
                    for (MapRecord<String, Object, Object> record : messages) {
                        processMessage(record);
                    }
                }
            } catch (Exception e) {
                if (running) {
                    log.error("消费消息异常", e);
                }
            }
        }
    }

    /**
     * 处理单条订单消息
     *
     * @param record Redis Stream 消息记录
     */
    private void processMessage(MapRecord<String, Object, Object> record) {
        try {
            // 取出消息体中的 "data" 字段（JSON 字符串）
            Object dataObj = record.getValue().get("data");
            if (dataObj == null) {
                log.warn("消息缺少 data 字段，recordId={}", record.getId());
                return;
            }
            String data = dataObj.toString();

            // 反序列化为订单创建请求
            OrderCreateRequest request = objectMapper.readValue(data, OrderCreateRequest.class);

            // 调用订单服务创建订单
            Order order = orderService.createOrder(request);

            // 写入 requestId -> orderNo 映射，供前端轮询查询（30 分钟过期）
            redisTemplate.opsForValue().set(
                    RedisKeys.orderRequest(record.getId().getValue()),
                    order.getOrderNo(),
                    Duration.ofMinutes(30)
            );

            // 确认消息已消费（XACK）
            redisTemplate.opsForStream().acknowledge(streamKey, groupName, record.getId());
            log.debug("订单消息处理成功，recordId={}", record.getId());
        } catch (Exception e) {
            // 不 ACK，等待重试
            log.error("处理消息失败，不ACK，等待重试，recordId={}", record.getId(), e);
        }
    }

    /**
     * 销毁前停止消费线程
     */
    @PreDestroy
    public void shutdown() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        log.info("订单消费者已停止");
    }
}
