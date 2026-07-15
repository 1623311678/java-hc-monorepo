package com.hc.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU — 库存单元 (商品的具体规格)
 * 例: iPhone 15 Pro 256G 钛金色
 *
 * 秒杀扣库存就是操作这个表
 */
@Data
@TableName("t_product_sku")
public class ProductSku {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long spuId;            // 关联 SPU
    private String skuName;       // SKU 名称
    private String specValues;    // 规格值 (JSON: {"颜色":"钛金色","容量":"256G"})
    private BigDecimal price;     // 售价
    private BigDecimal originalPrice; // 原价
    private Integer stock;        // 库存数 ← 秒杀核心字段
    private Integer lockStock;    // 锁定库存 (已下单未支付)
    private String image;         // SKU 图片

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    /**
     * 可售库存 = 总库存 - 锁定库存
     */
    public Integer getAvailableStock() {
        return stock - lockStock;
    }
}
