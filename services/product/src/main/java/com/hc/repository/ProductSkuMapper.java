package com.hc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.model.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * SKU 数据访问层
 *
 * 核心高并发操作: 扣减库存
 * 这里用 MySQL 行锁 + CAS 实现安全扣减
 */
@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSku> {

    /**
     * 扣减库存 (CAS 方式 — 乐观锁)
     *
     * WHERE stock >= count → 库存不足时影响行数 = 0，秒杀失败
     * 这条 SQL 利用 MySQL 行锁保证原子性
     */
    @Update("UPDATE t_product_sku SET stock = stock - #{count}, " +
            "lock_stock = lock_stock + #{count} " +
            "WHERE id = #{skuId} AND stock >= #{count}")
    int deductStock(@Param("skuId") Long skuId, @Param("count") Integer count);

    /**
     * 释放锁定库存 (订单取消/超时)
     */
    @Update("UPDATE t_product_sku SET lock_stock = lock_stock - #{count} " +
            "WHERE id = #{skuId} AND lock_stock >= #{count}")
    int releaseLockStock(@Param("skuId") Long skuId, @Param("count") Integer count);

    /**
     * 确认扣减: 锁定库存 → 已售出 (支付成功)
     */
    @Update("UPDATE t_product_sku SET lock_stock = lock_stock - #{count} " +
            "WHERE id = #{skuId} AND lock_stock >= #{count}")
    int confirmDeduct(@Param("skuId") Long skuId, @Param("count") Integer count);
}
