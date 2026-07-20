package com.hc.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hc.model.ProductSku;
import com.hc.repository.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 商品库存服务 — 秒杀核心
 *
 * 三级防护:
 *   1. Redis 信号量 — 快速过滤超卖请求 (纳秒级)
 *   2. Redisson 分布式锁 — 防止并发扣减 (毫秒级)
 *   3. MySQL CAS — 最终一致性保底 (行锁)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService extends ServiceImpl<ProductSkuMapper, ProductSku> {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;

    private static final String STOCK_KEY = "stock:sku:";
    private static final String LOCK_KEY = "lock:sku:";

    /**
     * 秒杀扣库存 — 三级防护
     *
     * @return true=抢到, false=没抢到
     */
    public boolean deductStockForSeckill(Long skuId, Integer count) {
        // ===== 第 1 级: Redis 信号量 (最快) =====
        // 库存预热到 Redis，用信号量控制并发数
        String semKey = STOCK_KEY + skuId;
        RSemaphore semaphore = redissonClient.getSemaphore(semKey);
        boolean acquired = semaphore.tryAcquire(count);
        if (!acquired) {
            log.warn("[秒杀] Redis 信号量拒绝 — skuId:{}, count:{}", skuId, count);
            return false;
        }

        try {
            // ===== 第 2 级: Redisson 分布式锁 =====
            // 防止同一 SKU 并发扣减导致超卖
            String lockKey = LOCK_KEY + skuId;
            RLock lock = redissonClient.getLock(lockKey);
            boolean locked = lock.tryLock(0, 3, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[秒杀] 获取分布式锁失败 — skuId:{}", skuId);
                semaphore.release(count); // 归还信号量
                return false;
            }

            try {
                // ===== 第 3 级: MySQL CAS 扣减 =====
                // UPDATE ... WHERE stock >= count
                int rows = baseMapper.deductStock(skuId, count);
                if (rows > 0) {
                    log.info("[秒杀] 扣减成功 — skuId:{}, count:{}", skuId, count);
                    return true;
                } else {
                    log.warn("[秒杀] MySQL 库存不足 — skuId:{}", skuId);
                    semaphore.release(count); // 归还信号量
                    return false;
                }
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            semaphore.release(count);
            return false;
        }
    }

    /**
     * 库存预热 — 秒杀开始前，把库存加载到 Redis
     *
     * 调用时机: 秒杀活动开始时由管理后台触发
     */
    public void warmUpStock(Long skuId) {
        ProductSku sku = getById(skuId);
        if (sku == null) return;

        String key = STOCK_KEY + skuId;
        // 用信号量初始化库存数
        RSemaphore semaphore = redissonClient.getSemaphore(key);
        semaphore.trySetPermits(sku.getAvailableStock());

        // 同时缓存 SKU 信息
        redisTemplate.opsForValue().set(
            "sku:info:" + skuId,
            sku.getSkuName(),
            24, TimeUnit.HOURS
        );

        log.info("[库存预热] skuId:{}, 可售库存:{}", skuId, sku.getAvailableStock());
    }

    /**
     * 释放锁定库存 — 订单取消/超时未支付
     */
    public void releaseLockStock(Long skuId, Integer count) {
        baseMapper.releaseLockStock(skuId, count);
        // 归还 Redis 信号量
        RSemaphore semaphore = redissonClient.getSemaphore(STOCK_KEY + skuId);
        semaphore.release(count);
        log.info("[释放库存] skuId:{}, count:{}", skuId, count);
    }
}
