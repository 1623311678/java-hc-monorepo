package com.hc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.model.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据访问层
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
