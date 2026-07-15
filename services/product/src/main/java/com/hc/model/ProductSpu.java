package com.hc.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SPU — 标准商品单元 (一件商品)
 * 例: iPhone 15 Pro
 */
@Data
@TableName("t_product_spu")
public class ProductSpu {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;           // 商品名称
    private String subtitle;       // 副标题
    private Long categoryId;       // 分类 ID
    private Long brandId;          // 品牌 ID
    private BigDecimal price;      // 最低价
    private String mainImage;      // 主图 URL
    private String images;         // 图片列表 (JSON)
    private String detail;         // 商品详情 (HTML)

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;       // 逻辑删除
}
