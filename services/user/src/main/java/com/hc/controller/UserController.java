package com.hc.controller;

import com.hc.dto.ApiResponse;
import com.hc.dto.LoginRequest;
import com.hc.dto.LoginResponse;
import com.hc.dto.RegisterRequest;
import com.hc.dto.UserProfile;
import com.hc.service.UserAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户认证")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserAuthService userAuthService;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ApiResponse<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(userAuthService.register(request));
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userAuthService.login(request));
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public ApiResponse<UserProfile> me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return ApiResponse.fail("未登录");
        }
        String token = authorization.substring("Bearer ".length());
        return ApiResponse.success(userAuthService.me(token));
    }
}
