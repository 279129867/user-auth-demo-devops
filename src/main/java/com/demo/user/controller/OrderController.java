package com.demo.user.controller;

import com.demo.user.common.ApiResponse;
import com.demo.user.entity.Order;
import com.demo.user.service.OrderService;
import com.demo.user.kafka.OrderProducer;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderProducer orderProducer;

    public OrderController(OrderService orderService, OrderProducer orderProducer) {
        this.orderService = orderService;
        this.orderProducer = orderProducer;
    }

    /**
     * 创建订单：落库 PENDING -> 发 Kafka 消息 -> 立即返回。
     * 后续支付/通知由 Kafka 消费者异步完成。
     */
    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> createOrder(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(String.valueOf(body.get("userId")));
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String productName = body.get("productName") == null ? null : String.valueOf(body.get("productName"));

        Order order = orderService.createOrder(userId, amount, productName);

        // 发送到 Kafka，异步处理
        orderProducer.sendOrderCreated(order.getId(), order.getUserId(), order.getAmount());

        return ApiResponse.success(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "message", "订单已创建，正在异步处理中"
        ));
    }

    /** 查询订单状态（用于验证异步处理结果） */
    @GetMapping("/{id}")
    public ApiResponse<Order> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrder(id);
        return ApiResponse.success(order);
    }
}
