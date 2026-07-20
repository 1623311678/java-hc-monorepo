package com.hc.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功返回
 */
@Data
@Builder
public class LoginResponse {

    private String token;

    private UserProfile user;
}
