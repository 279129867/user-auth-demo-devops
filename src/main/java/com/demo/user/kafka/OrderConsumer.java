package com.demo.user.kafka;

import com.demo.user.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka 消费者：订阅 order-events，收到订单创建消息后异步把订单状态改为 PAID。
 *
 * 演示核心：生产者发送消息后主流程立刻返回，消费者异步完成后续业务 ——
 * 这就是消息队列的「异步解耦 + 削峰」落地场景。
 */
@Component
public class OrderConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    private final OrderService orderService;

    public OrderConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = KafkaConfig.ORDER_TOPIC, groupId = "order-consumer-group")
    public void onOrderCreated(Map<String, Object> message) {
        Object orderIdObj = message.get("orderId");
        if (orderIdObj == null) {
            log.warn("收到非法订单消息: {}", message);
            return;
        }
        Long orderId = Long.valueOf(String.valueOf(orderIdObj));
        log.info("收到订单创建事件，开始异步处理: orderId={}", orderId);

        // 模拟异步业务处理耗时（体现异步处理场景）
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 把订单从 PENDING 更新为 PAID
        orderService.markPaid(orderId);
        log.info("订单异步处理完成: orderId={} -> PAID", orderId);
    }
}
