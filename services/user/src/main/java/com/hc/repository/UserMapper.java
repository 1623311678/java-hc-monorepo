package com.hc.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
