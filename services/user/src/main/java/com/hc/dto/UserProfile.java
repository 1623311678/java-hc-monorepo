package com.hc.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 用户信息返回
 */
@Data
@Builder
public class UserProfile {

    private Long id;

    private String username;

    private String phone;

    private String email;

    private String nickname;

    private String avatar;

    private Integer status;
}
