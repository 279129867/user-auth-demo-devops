package com.demo.user.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka 配置：定义业务 topic
 */
@Configuration
public class KafkaConfig {

    /** 订单事件 topic 名 */
    public static final String ORDER_TOPIC = "order-events";

    /**
     * 创建 topic（副本数 1，分区 1，适合单节点演示）
     */
    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name(ORDER_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
