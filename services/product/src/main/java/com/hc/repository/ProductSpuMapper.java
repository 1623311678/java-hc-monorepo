package com.hc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.model.ProductSpu;
import org.apache.ibatis.annotations.Mapper;

/**
 * SPU 数据访问层
 */
@Mapper
public interface ProductSpuMapper extends BaseMapper<ProductSpu> {
}
