package com.demo.user.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Kafka 生产者：把订单创建事件发送到 order-events topic。
 *
 * 业务意义：订单创建后主流程「立即返回」，支付/通知等后续动作交给 Kafka
 * 异步消费，体现 MQ 的异步解耦 + 削峰填谷。
 */
@Component
public class OrderProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 发送订单创建消息。
     * key 用订单 id，保证同一订单的消息按顺序消费。
     */
    public void sendOrderCreated(Long orderId, Long userId, BigDecimal amount) {
        Map<String, Object> payload = Map.of(
                "orderId", orderId,
                "userId", userId,
                "amount", amount
        );
        kafkaTemplate.send(KafkaConfig.ORDER_TOPIC, String.valueOf(orderId), payload);
        log.info("订单消息已发送到 Kafka: topic={}, orderId={}", KafkaConfig.ORDER_TOPIC, orderId);
    }
}
