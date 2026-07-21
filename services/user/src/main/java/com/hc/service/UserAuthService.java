package com.hc.service;  // 包名 = 文件所在目录，相当于前端的文件路径

// ========== import 区域 ==========
// 相当于前端的 import xxx from "xxx"，Java 要写完整的包路径

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;  // 查询条件构造器 ≈ Prisma 的 where
import com.hc.dto.LoginRequest;       // 登录请求参数类型 ≈ 前端的 type LoginRequest
import com.hc.dto.LoginResponse;      // 登录返回类型 ≈ 前端的 type LoginResponse
import com.hc.dto.RegisterRequest;    // 注册请求参数类型
import com.hc.dto.UserProfile;        // 用户信息(脱敏版) ≈ 前端的 type UserProfile
import com.hc.model.User;             // 数据库用户实体 ≈ 前端的 type User（含密码）
import com.hc.repository.UserMapper;  // 数据库操作工具 ≈ 前端的 prisma.user
import com.hc.util.JwtUtil;           // JWT工具 ≈ 前端的 jsonwebtoken 库
import lombok.RequiredArgsConstructor;  // Lombok注解：自动生成构造函数，用于依赖注入
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;  // 密码加密工具
import org.springframework.stereotype.Service;  // 标记为业务服务，Spring会自动管理
import org.springframework.util.StringUtils;   // 字符串工具类 ≈ 前端的 !!str.trim()

/**
 * 用户认证服务
 * ≈ 前端 Zustand store 里的核心业务逻辑
 */
@Service                      // 告诉Spring：我是业务服务，启动时创建我，放进容器
@RequiredArgsConstructor      // Lombok：自动生成包含所有final字段的构造函数
                              // 效果 = Spring通过构造函数把userMapper和jwtUtil注入进来
                              // ≈ 前端 const store = create() 时自动获取依赖
public class UserAuthService {

    // ========== 依赖声明 ==========
    // private = 只有这个类内部能用 ≈ 闭包里的私有变量
    // final = 赋值后不能改 ≈ const
    // Spring启动时会自动创建这些对象，通过构造函数传进来

    private final UserMapper userMapper;  // 数据库操作工具 ≈ const prisma = new PrismaClient()
                                          // 用来查/插/改/删 t_user 表

    private final JwtUtil jwtUtil;        // JWT工具 ≈ const jwt = require("jsonwebtoken")
                                          // 用来生成token、解析token

    // 这个不用Spring注入，因为它不需要Spring管理，直接new一个就行
    // BCrypt = 密码加密算法，同一明文每次加密结果不同（有随机盐），更安全
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    // ≈ const bcrypt = require("bcrypt")


    // ==========================================
    // 方法1：注册
    // 接收注册参数 → 校验 → 加密密码 → 写入数据库 → 返回用户信息
    // ==========================================
    public UserProfile register(RegisterRequest request) {
        // public = 任何类都能调
        // 返回类型 UserProfile = 脱敏后的用户信息（不含密码）
        // 参数 request = 前端传过来的 { username, password, phone, email, nickname }

        // ------ 第一步：查用户名是否已存在 ------
        User existing = userMapper.selectOne(
            // userMapper.selectOne(条件) = 从数据库查一条记录
            // ≈ prisma.user.findFirst({ where: { ... } })
            // 如果查不到返回 null

            new LambdaQueryWrapper<User>()
                // LambdaQueryWrapper = 查询条件构造器 ≈ Prisma 的 where
                // <User> = 告诉它查的是 t_user 表
                .eq(User::getUsername, request.getUsername())
                // .eq(字段, 值) = WHERE 字段 = 值
                // User::getUsername ≈ (u) => u.username，类型安全的字段引用
                // request.getUsername() = 前端传过来的用户名
                // 生成的SQL: WHERE username = 'xxx'
        );

        if (existing != null) {
            // existing != null = 数据库里已经有一条了 = 用户名已被注册
            throw new IllegalArgumentException("用户名已存在");
            // throw = 抛异常，中断方法执行 ≈ 前端 throw new Error("用户名已存在")
            // 异常会被 GlobalExceptionHandler 捕获，返回 { code: 1, message: "用户名已存在" }
        }


        // ------ 第二步：查手机号是否已存在（如果填了的话） ------
        if (StringUtils.hasText(request.getPhone())) {
            // StringUtils.hasText(str) ≈ str && str.trim() !== ""
            // 手机号是可选的，只有填了才查重

            User phoneUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, request.getPhone())
                    // 生成的SQL: WHERE phone = '13800138000'
            );

            if (phoneUser != null) {
                throw new IllegalArgumentException("手机号已存在");
                // 手机号已被注册，抛异常
            }
        }


        // ------ 第三步：创建用户对象，准备写入数据库 ------
        User user = new User();
        // new User() ≈ 前端 const user: User = {}，先创建空对象

        user.setUsername(request.getUsername());
        // 把前端传的 username 设到 user 对象上
        // ≈ user.username = request.username

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        //               ↑ 关键！密码加密
        // passwordEncoder.encode(明文) = BCrypt加密
        // "123456" → "$2a$10$N9qo8uLOickgx2ZMRZoMyeIh3..."
        // 每次加密结果不同（有随机盐），但 matches() 验证时能正确对比
        // ≈ const hashed = await bcrypt.hash(password, 10)

        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        // 可选字段，前端没传就是 null

        user.setNickname(
            StringUtils.hasText(request.getNickname())
                ? request.getNickname()
                : request.getUsername()
        );
        // 三元运算符 ≈ 前端 user.nickname = request.nickname || request.username
        // 如果填了昵称就用昵称，否则用用户名当昵称

        user.setStatus(1);
        // 1=正常，0=禁用，新注册用户默认正常


        // ------ 第四步：写入数据库 ------
        userMapper.insert(user);
        // 生成的SQL: INSERT INTO t_user (username, password, phone, email, nickname, status)
        //            VALUES ('test', '$2a$10$...', '138...', NULL, 'test', 1)
        // ≈ await prisma.user.create({ data: { username, password, ... } })
        // 插入后 user.getId() 能拿到自增的ID（MyBatis-Plus自动回填）

        return toProfile(user);
        // 调用私有方法 toProfile，把 User → UserProfile（脱敏）
        // 返回给前端的数据不含密码
    }


    // ==========================================
    // 方法2：登录
    // 接收登录参数 → 查用户 → 验密码 → 生成token → 返回
    // ==========================================
    public LoginResponse login(LoginRequest request) {
        // 返回类型 LoginResponse = { token: string, user: UserProfile }

        // ------ 第一步：按用户名查数据库 ------
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
            // 生成的SQL: SELECT * FROM t_user WHERE username = 'testuser'
        );

        // ------ 第二步：验证用户名和密码 ------
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            //                     ↑ 验证密码
            // passwordEncoder.matches(明文, 密文) = BCrypt验证
            // ≈ await bcrypt.compare(inputPassword, storedHash)
            // 返回 true = 密码正确，false = 密码错误

            // 两种情况都报同样的错：
            //   user == null → 用户名不存在
            //   !matches → 密码错误
            // 故意不说具体哪个错，防止攻击者枚举用户名
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // ------ 第三步：检查账号状态 ------
        if (user.getStatus() != null && user.getStatus() == 0) {
            // status = 0 = 被封号了
            throw new IllegalArgumentException("账号已禁用");
        }

        // ------ 第四步：生成JWT token ------
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        // 把 userId 和 username 编进 token
        // 生成的 token ≈ "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIi..."
        // ≈ const token = jwt.sign({ userId: user.id, username: user.username }, secret, { expiresIn: '24h' })

        // ------ 第五步：组装返回数据 ------
        return LoginResponse.builder()
            .token(token)              // token 字段
            .user(toProfile(user))     // user 字段（脱敏后的）
            .build();                  // 构建完成
            // ≈ 前端 return { token, user: toProfile(user) }

            // 最终返回给前端的JSON：
            // {
            //   "code": 0,
            //   "message": "success",
            //   "data": {
            //     "token": "eyJhbG...",
            //     "user": { "id": 1, "username": "test", "nickname": "测试", ... }
            //   }
            // }
    }


    // ==========================================
    // 方法3：获取当前登录用户信息
    // 接收token → 解析userId → 查数据库 → 返回用户信息
    // ==========================================
    public UserProfile me(String token) {
        // 前端请求 GET /user/me 时，Controller 从 Authorization 头取出 token 传过来

        Long userId = jwtUtil.extractUserId(token);
        // 从 JWT token 里解析出 userId
        // ≈ const decoded = jwt.verify(token, secret); const userId = decoded.userId
        // 如果 token 过期或被篡改，这里会抛 JwtException

        User user = userMapper.selectById(userId);
        // 按ID查数据库
        // 生成的SQL: SELECT * FROM t_user WHERE id = 1
        // ≈ const user = await prisma.user.findUnique({ where: { id: userId } })

        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
            // token 有效但用户已被删除（极端情况）
        }

        return toProfile(user);
        // 返回脱敏后的用户信息
    }


    // ==========================================
    // 私有方法：User → UserProfile 转换（脱敏）
    // 只有这个类内部能调，外部不能
    // ==========================================
    private UserProfile toProfile(User user) {
        // private = 私有 ≈ 闭包里的内部函数，外部调不到

        return UserProfile.builder()
            // UserProfile 有 @Builder 注解，才能用这种链式写法
            // ≈ 前端 return { id: user.id, username: user.username, ... }

            .id(user.getId())              // 用户ID
            .username(user.getUsername()) // 用户名
            .phone(user.getPhone())       // 手机号
            .email(user.getEmail())       // 邮箱
            .nickname(user.getNickname()) // 昵称
            .avatar(user.getAvatar())     // 头像URL
            .status(user.getStatus())     // 状态 1正常 0禁用
            // ⚠️ 注意：没有 .password ！！！
            // ⚠️ 注意：没有 .createTime ！！！
            // ⚠️ 注意：没有 .updateTime ！！！
            // 这就是"脱敏"——过滤掉不该给前端看的数据

            .build();
            // 收尾，把上面设的所有字段打包成一个 UserProfile 对象
            // ≈ 前端 } 闭合大括号
    }
}
