package com.hc.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单 — 电商核心实体
 */
@Data
@TableName("t_order")
public class Order {
    @TableId(type = IdType.ASSIGN_ID)  // 雪花算法 ID
    private Long id;

    private Long userId;
    private String orderNo;         // 订单号 (业务ID)
    private Long skuId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;

    /** 订单状态: 0-待支付 1-已支付 2-已发货 3-已完成 4-已取消 5-已退款 */
    private Integer status;

    private LocalDateTime payTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime closeTime;  // 超时关闭时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
