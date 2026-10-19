package com.demo.user.service;

import com.demo.user.common.BizException;
import com.demo.user.entity.Order;
import com.demo.user.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 创建订单：落库为 PENDING 状态。
     * 这里不处理后续（支付/通知），由 Kafka 消费者异步完成 —— 体现 MQ 异步解耦。
     */
    @Transactional
    public Order createOrder(Long userId, BigDecimal amount, String productName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(400, "订单金额必须大于0");
        }
        Order order = new Order();
        order.setUserId(userId);
        order.setAmount(amount);
        order.setProductName(productName == null || productName.isBlank() ? "默认商品" : productName);
        order.setStatus("PENDING");
        return orderRepository.save(order);
    }

    /** 由 Kafka 消费者调用：把订单状态从 PENDING 更新为 PAID */
    @Transactional
    public void markPaid(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException(404, "订单不存在: " + orderId));
        order.setStatus("PAID");
        orderRepository.save(order);
    }

    /** 查询订单（用于验证异步处理结果） */
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException(404, "订单不存在: " + orderId));
    }
}
