package com.hc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hc.model.ProductSku;
import com.hc.model.ProductSpu;
import com.hc.repository.ProductSkuMapper;
import com.hc.repository.ProductSpuMapper;
import com.hc.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品 API
 */
@Tag(name = "商品服务")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final StockService stockService;

    // ========== 商品列表 (分页) ==========

    @Operation(summary = "商品分页列表")
    @GetMapping("/spu/page")
    public Page<ProductSpu> spuPage(
        @RequestParam(defaultValue = "1") Integer current,
        @RequestParam(defaultValue = "20") Integer size,
        @RequestParam(required = false) Long categoryId
    ) {
        Page<ProductSpu> page = new Page<>(current, size);
        LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(ProductSpu::getCategoryId, categoryId);
        }
        wrapper.orderByDesc(ProductSpu::getCreateTime);
        return spuMapper.selectPage(page, wrapper);
    }

    // ========== 商品详情 (Redis 缓存) ==========

    @Operation(summary = "商品详情")
    @GetMapping("/spu/{id}")
    @Cacheable(value = "product:spu", key = "#id")
    public ProductSpu spuDetail(@PathVariable Long id) {
        return spuMapper.selectById(id);
    }

    // ========== SKU 列表 ==========

    @Operation(summary = "商品 SKU 列表")
    @GetMapping("/sku/list")
    public List<ProductSku> skuList(@RequestParam Long spuId) {
        return skuMapper.selectList(
            new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, spuId)
        );
    }

    // ========== 秒杀 ==========

    @Operation(summary = "秒杀抢购")
    @PostMapping("/seckill/{skuId}")
    public String seckill(@PathVariable Long skuId) {
        boolean success = stockService.deductStockForSeckill(skuId, 1);
        return success ? "抢购成功！请尽快支付" : "已售罄，下次再来";
    }

    // ========== 库存预热 ==========

    @Operation(summary = "库存预热 (管理端)")
    @PostMapping("/stock/warmup/{skuId}")
    public String warmUp(@PathVariable Long skuId) {
        stockService.warmUpStock(skuId);
        return "库存预热完成";
    }
}
