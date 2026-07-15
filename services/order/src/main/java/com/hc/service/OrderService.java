package com.hc.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.model.Order;
import com.hc.repository.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务 — 秒杀下单核心
 *
 * 流程:
 *   1. 创建订单 (MySQL)
 *   2. 发送库存扣减消息 (RabbitMQ)
 *   3. 设置超时关闭 (Redis 延迟队列, 30分钟)
 *   4. Seata 分布式事务保证一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService extends ServiceImpl<OrderMapper, Order> {

    private final RabbitTemplate rabbitTemplate;
    private final RedissonClient redissonClient;

    private static final String ORDER_DELAY_QUEUE = "order:delay:queue";
    private static final int PAY_TIMEOUT_MINUTES = 30;

    /**
     * 秒杀下单
     *
     * @Transactional + Seata 保证:
     *   订单创建 + 库存扣减 要么同时成功，要么同时回滚
     */
    @Transactional
    public Order createSeckillOrder(Long userId, Long skuId, Integer quantity, BigDecimal price) {
        // 1. 创建订单
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setSkuId(skuId);
        order.setQuantity(quantity);
        order.setTotalAmount(price.multiply(BigDecimal.valueOf(quantity)));
        order.setPayAmount(order.getTotalAmount());
        order.setStatus(0);  // 待支付
        order.setCreateTime(LocalDateTime.now());
        save(order);

        log.info("[下单] 订单创建成功 — orderNo:{}, userId:{}, skuId:{}", order.getOrderNo(), userId, skuId);

        // 2. 发送库存扣减消息到 RabbitMQ
        rabbitTemplate.convertAndSend(
            "stock.exchange",
            "stock.deduct",
            skuId + ":" + quantity
        );

        // 3. 延迟队列 — 30 分钟后自动关闭未支付订单
        RQueue<String> queue = redissonClient.getQueue(ORDER_DELAY_QUEUE);
        RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(queue);
        delayedQueue.offer(order.getOrderNo(), PAY_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        return order;
    }

    /**
     * 关闭超时订单 + 释放库存
     */
    @Transactional
    public void closeOrder(String orderNo) {
        Order order = lambdaQuery().eq(Order::getOrderNo, orderNo).one();
        if (order == null || order.getStatus() != 0) {
            return;  // 已支付或已关闭，跳过
        }

        // 更新订单状态为已取消
        lambdaUpdate()
            .eq(Order::getOrderNo, orderNo)
            .eq(Order::getStatus, 0)  // CAS: 只关闭待支付的
            .set(Order::getStatus, 4)
            .set(Order::getCloseTime, LocalDateTime.now())
            .update();

        // 发送库存释放消息
        rabbitTemplate.convertAndSend(
            "stock.exchange",
            "stock.release",
            order.getSkuId() + ":" + order.getQuantity()
        );

        log.info("[关单] 超时订单已关闭 — orderNo:{}", orderNo);
    }

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
