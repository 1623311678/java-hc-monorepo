# User 服务代码详解 — 前端开发者视角

> 本文档专为前端开发者编写，逐文件、逐行拆解 Java user 服务的每一行代码，用前端概念对照解释。

---

## 目录

1. [整体架构](#1-整体架构)
2. [文件结构总览](#2-文件结构总览)
3. [启动入口 — UserApplication.java](#3-启动入口--userapplicationjava)
4. [数据库实体 — model/User.java](#4-数据库实体--modeluserjava)
5. [数据库操作 — repository/UserMapper.java](#5-数据库操作--repositoryusermapperjava)
6. [请求参数 DTO — dto/RegisterRequest.java & LoginRequest.java](#6-请求参数-dto--dtoregisterrequestjava--loginrequestjava)
7. [返回数据 DTO — dto/LoginResponse.java & UserProfile.java & ApiResponse.java](#7-返回数据-dto--dtonloginresponsejava--userprofilejava--apiresponsejava)
8. [JWT 工具 — util/JwtUtil.java](#8-jwt-工具--utiljwtutiljava)
9. [核心业务逻辑 — service/UserAuthService.java](#9-核心业务逻辑--serviceuserauthservicejava)
10. [API 路由 — controller/UserController.java](#10-api-路由--controllerusercontrollerjava)
11. [全局错误处理 — controller/GlobalExceptionHandler.java](#11-全局错误处理--controllerglobalexceptionhandlerjava)
12. [配置文件 — application.yml](#12-配置文件--applicationyml)
13. [完整请求流程](#13-完整请求流程)
14. [Java vs 前端概念对照表](#14-java-vs-前端概念对照表)

---

## 1. 整体架构

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────┐
│  前端 Next.js │────▶│  Gateway    │────▶│  User 服务   │────▶│  MySQL  │
│  localhost:   │     │  7100       │     │  7101       │     │  3306   │
│  3000         │     │  (路由转发)  │     │  (业务逻辑)  │     │ (数据)  │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────┘
```

**前端发请求 → Gateway 转发 → User 服务处理 → 读写 MySQL → 返回 JSON**

类比前端：
- 前端 `fetch("/api/user/login")` ≈ 浏览器发 HTTP 请求
- Gateway ≈ Next.js rewrites 代理（路径转发）
- User 服务 ≈ Next.js API Route（处理逻辑）
- MySQL ≈ 数据库

---

## 2. 文件结构总览

```
services/user/src/main/java/com/hc/
├── UserApplication.java              ← 启动入口（≈ next dev 启动）
├── model/
│   └── User.java                     ← 数据库表映射（≈ TypeScript 的 type 定义）
├── repository/
│   └── UserMapper.java               ← 数据库操作（≈ Prisma Model）
├── dto/
│   ├── RegisterRequest.java           ← 注册请求参数（≈ 前端表单数据类型）
│   ├── LoginRequest.java             ← 登录请求参数
│   ├── LoginResponse.java            ← 登录返回数据（≈ 前端 API 响应类型）
│   ├── UserProfile.java             ← 用户信息（脱敏，不含密码）
│   └── ApiResponse.java             ← 通用返回包装（≈ { code, message, data }）
├── util/
│   └── JwtUtil.java                  ← JWT 工具（≈ 前端 token 生成/解析）
├── service/
│   └── UserAuthService.java          ← 业务逻辑（≈ 前端 store 的核心逻辑）
└── controller/
    ├── UserController.java           ← API 路由（≈ app/api/route.ts）
    └── GlobalExceptionHandler.java   ← 全局错误处理（≈ try/catch 兜底）
```

**分层逻辑：**

| 层 | 职责 | 前端类比 |
|---|---|---|
| Controller | 接收请求、返回响应 | `app/api/route.ts` |
| Service | 业务逻辑 | Zustand store / hooks |
| Repository | 数据库操作 | Prisma Client |
| Model | 数据结构定义 | TypeScript `type` |
| DTO | 请求/响应的数据格式 | TypeScript `interface` |
| Util | 工具方法 | `lib/*.ts` |

---

## 3. 启动入口 — UserApplication.java

```java
package com.hc;  // 包名 ≈ 前端文件的目录路径
```

```java
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
```
`import` = 前端的 `import`。Java 要写完整包路径，不像前端能用 `@/` 简写。

```java
@SpringBootApplication
```
**一键开启 Spring Boot 全部功能**，等于同时加了三个注解：
- `@Configuration` — 我是个配置类
- `@EnableAutoConfiguration` — 自动配置，不用手动写
- `@ComponentScan` — 扫描同包下的 @Service、@Controller 等

≈ 前端：`npx create-next-app` 创建项目后自动配好一切。

```java
@EnableDiscoveryClient
```
**注册到 Nacos**。告诉注册中心"我上线了，地址是 xxx"，让 Gateway 能找到我。
≈ 微前端里把子应用注册到主应用。

```java
@MapperScan("com.hc.repository")
```
**告诉 MyBatis-Plus：去 `com.hc.repository` 包下找所有 Mapper 接口**。
≈ `prisma generate` 自动识别 model。

```java
public class UserApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
```
程序入口。`main` 方法 ≈ 前端 `next start` 的内部调用。Java 需要显式写出来。

---

## 4. 数据库实体 — model/User.java

```java
@Data                     // Lombok：自动生成 getter/setter/toString
@TableName("t_user")      // 对应数据库的 t_user 表
public class User {

    @TableId(type = IdType.AUTO)   // 主键，自增
    private Long id;               // Long ≈ 前端 number（范围更大）

    private String username;       // String ≈ 前端 string
    private String password;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private Integer status;        // Integer ≈ 前端 number（1=正常，0=禁用）

    private LocalDateTime createTime;   // ≈ 前端 Date
    private LocalDateTime updateTime;
}
```

**与数据库表的对应关系：**

| Java 字段 (驼峰) | 数据库列 (下划线) | 类型 |
|---|---|---|
| `id` | `id` | BIGINT → Long |
| `username` | `username` | VARCHAR(50) → String |
| `password` | `password` | VARCHAR(200) → String（BCrypt 密文很长） |
| `phone` | `phone` | VARCHAR(20) → String |
| `email` | `email` | VARCHAR(100) → String |
| `nickname` | `nickname` | VARCHAR(50) → String |
| `avatar` | `avatar` | VARCHAR(500) → String |
| `status` | `status` | TINYINT → Integer |
| `createTime` | `create_time` | DATETIME → LocalDateTime |
| `updateTime` | `update_time` | DATETIME → LocalDateTime |

MyBatis-Plus 自动处理驼峰 ↔ 下划线的转换。

**`@Data` 省了什么？** 没有它，你要手写 20+ 行：

```java
// 没有 @Data 时，每个字段都要手写：
public Long getId() { return id; }
public void setId(Long id) { this.id = id; }
public String getUsername() { return username; }
public void setUsername(String username) { this.username = username; }
// ... 还有 8 个字段 × 2 = 16 行 getter/setter
// 加上 toString、equals、hashCode 又 20 行
```

---

## 5. 数据库操作 — repository/UserMapper.java

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

**接口是空的！** 但继承了 MyBatis-Plus 的所有 CRUD 方法：

| 继承来的方法 | 作用 | ≈ Prisma |
|---|---|---|
| `selectById(id)` | 按 ID 查一条 | `prisma.user.findUnique({ where: { id } })` |
| `selectOne(wrapper)` | 按条件查一条 | `prisma.user.findFirst({ where: { username } })` |
| `selectPage(page, wrapper)` | 分页查询 | `prisma.user.findMany({ skip, take })` |
| `insert(user)` | 插入一条 | `prisma.user.create({ data })` |
| `updateById(user)` | 按 ID 更新 | `prisma.user.update({ where, data })` |
| `deleteById(id)` | 按 ID 删除 | `prisma.user.delete({ where: { id } })` |

`interface` ≈ 前端 `interface`，只定义方法签名，MyBatis-Plus 在运行时自动生成实现。
`@Mapper` ≈ 告诉 Spring "帮我管理这个接口"。

---

## 6. 请求参数 DTO — dto/RegisterRequest.java & LoginRequest.java

### RegisterRequest.java

```java
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需在4-20之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 30, message = "密码长度需在6-30之间")
    private String password;

    @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
    private String phone;          // 可选，填了就必须合法

    private String email;          // 无注解 = 完全可选
    private String nickname;       // 完全可选
}
```

**注解校验 ≈ 前端表单验证：**

| Java 注解 | 作用 | ≈ 前端 |
|---|---|---|
| `@NotBlank` | 不能为空字符串 | `if (!username.trim())` |
| `@Size(min=4, max=20)` | 长度范围 | `if (username.length < 4 \|\| > 20)` |
| `@Pattern(regexp=...)` | 正则匹配 | `if (!/^1\d{10}$/.test(phone))` |

正则 `^$|^1\d{10}$` = 空字符串 OR 11 位手机号。

### LoginRequest.java

```java
@Data
public class LoginRequest {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}
```

和前端 `login/page.tsx` 里的 `{ username, password }` 一一对应。

---

## 7. 返回数据 DTO — dto/LoginResponse.java & UserProfile.java & ApiResponse.java

### LoginResponse.java

```java
@Data
@Builder
public class LoginResponse {
    private String token;
    private UserProfile user;
}
```

`@Builder` = Lombok 生成建造者模式，方便构造对象：

```java
// 有 @Builder：
LoginResponse.builder().token(token).user(profile).build();

// 没有 @Builder：
new LoginResponse(token, profile);  // 参数顺序容易搞混
```

### UserProfile.java

```java
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
    // ⚠️ 没有 password！这是脱敏后的数据，给前端看的
}
```

**`User`（数据库实体）vs `UserProfile`（返回前端）的区别：**

| 字段 | User (model) | UserProfile (dto) |
|---|---|---|
| password | ✅ 有 | ❌ 没有（脱敏） |
| createTime | ✅ 有 | ❌ 没有 |
| updateTime | ✅ 有 | ❌ 没有 |

### ApiResponse.java

```java
@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private Integer code;     // 0=成功，1=失败
    private String message;   // "success" 或 错误消息
    private T data;           // 实际数据，类型由泛型 T 决定

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(1, message, null);
    }
}
```

前端收到的 JSON 格式：

```json
{ "code": 0, "message": "success", "data": { "token": "eyJ...", "user": {...} } }
{ "code": 1, "message": "用户名已存在", "data": null }
```

`static` 方法 = 不需要 `new ApiResponse()` 就能调，直接 `ApiResponse.success(data)`。
≈ 前端 `Math.random()`、`Array.from()` 等。

---

## 8. JWT 工具 — util/JwtUtil.java

```java
@Component   // 告诉 Spring：放进容器，别人需要时自动注入
public class JwtUtil {

    @Value("${jwt.secret:mySecretKeyForJwtTokenGenerationMustBe256BitsLong2024!}")
    private String jwtSecret;
    // @Value ≈ process.env.JWT_SECRET || "默认值"

    @Value("${jwt.expiration:86400000}")
    private Long expiration;
    // 86400000ms = 24小时

    private SecretKey secretKey;

    @PostConstruct   // Spring 创建这个 Bean 后自动执行 ≈ useEffect
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
```

**生成 token：**

```java
    public String generateToken(Long userId, String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(String.valueOf(userId))     // 主题 = 用户ID
            .claim("username", username)          // 额外信息 = 用户名
            .issuedAt(new Date(now))             // 签发时间
            .expiration(new Date(now + expiration)) // 过期时间
            .signWith(secretKey)                 // 用密钥签名
            .compact();                          // 生成字符串
    }
```

生成的 token 结构：`header.payload.signature`

```
eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJ0ZXN0dXNlciI...xxx
↑ 算法信息               ↑ 数据（userId、username、时间）         ↑ 签名
```

**解析 token：**

```java
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)             // 验签
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());  // 取出 userId
    }
```

如果 token 被篡改或过期，`parseToken` 抛异常，被 GlobalExceptionHandler 捕获。

---

## 9. 核心业务逻辑 — service/UserAuthService.java

```java
@Service                    // 业务服务注解 ≈ 前端 store
@RequiredArgsConstructor    // 依赖注入
public class UserAuthService {

    private final UserMapper userMapper;          // Spring 自动注入
    private final JwtUtil jwtUtil;                 // Spring 自动注入
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
                                                    // 手动 new，Spring 不管理这个
```

### register 方法 — 注册

```java
public UserProfile register(RegisterRequest request) {
```

**第一步：查重 — 用户名是否已存在**

```java
    User existing = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
    );
    if (existing != null) {
        throw new IllegalArgumentException("用户名已存在");
    }
```

≈ 前端 Prisma：`prisma.user.findFirst({ where: { username } })`

生成的 SQL：`SELECT * FROM t_user WHERE username = ?`

**第二步：手机号查重（可选）**

```java
    if (StringUtils.hasText(request.getPhone())) {   // ≈ phone && phone.trim() !== ""
        User phoneUser = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getPhone, request.getPhone())
        );
        if (phoneUser != null) {
            throw new IllegalArgumentException("手机号已存在");
        }
    }
```

**第三步：创建用户对象**

```java
    User user = new User();
    user.setUsername(request.getUsername());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    //                               ↑ BCrypt 加密，明文 "123456" → "$2a$10$N9qo8u..."
    user.setPhone(request.getPhone());
    user.setEmail(request.getEmail());
    user.setNickname(
        StringUtils.hasText(request.getNickname())
            ? request.getNickname()
            : request.getUsername()
    );
    // 三元运算符 ≈ 前端 nickname || username
    user.setStatus(1);   // 1=正常，0=禁用
```

**第四步：写入数据库**

```java
    userMapper.insert(user);    // SQL: INSERT INTO t_user (...) VALUES (...)
    return toProfile(user);     // 转成脱敏的 UserProfile 返回
```

### login 方法 — 登录

```java
public LoginResponse login(LoginRequest request) {
    User user = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
    );
```

按用户名查数据库。

```java
    if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw new IllegalArgumentException("用户名或密码错误");
    }
```

`passwordEncoder.matches(明文, 密文)` = BCrypt 验证。

⚠️ **安全提示**：不说"用户名不存在"还是"密码错误"，统一返回"用户名或密码错误"，防止攻击者枚举用户名。

```java
    if (user.getStatus() != null && user.getStatus() == 0) {
        throw new IllegalArgumentException("账号已禁用");
    }
```

状态检查。

```java
    String token = jwtUtil.generateToken(user.getId(), user.getUsername());
    return LoginResponse.builder()
        .token(token)
        .user(toProfile(user))
        .build();
```

生成 JWT + 返回用户信息。前端收到的 JSON：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbG...",
    "user": { "id": 1, "username": "testuser", "nickname": "测试用户" }
  }
}
```

前端 store 对应处理：`set({ token: res.data.token, user: res.data.user })`

### me 方法 — 获取当前用户

```java
public UserProfile me(String token) {
    Long userId = jwtUtil.extractUserId(token);   // 从 token 解出 userId
    User user = userMapper.selectById(userId);     // 查数据库
    if (user == null) {
        throw new IllegalArgumentException("用户不存在");
    }
    return toProfile(user);
}
```

刷新页面时，前端把 localStorage 里的 token 传过来，后端解析 token → 查数据库 → 返回最新用户信息。

### toProfile 私有方法 — 实体转 DTO

```java
private UserProfile toProfile(User user) {
    return UserProfile.builder()
        .id(user.getId())
        .username(user.getUsername())
        .phone(user.getPhone())
        .email(user.getEmail())
        .nickname(user.getNickname())
        .avatar(user.getAvatar())
        .status(user.getStatus())
        .build();
    // ⚠️ password 没有拷贝！这就是脱敏
}
```

`private` = 只有这个类内部能用。≈ 前端闭包或 `#` 私有字段。

---

## 10. API 路由 — controller/UserController.java

```java
@Tag(name = "用户认证")        // Swagger 文档分组
@RestController               // 处理 HTTP 请求，返回 JSON
@RequestMapping("/user")      // 所有接口以 /user 开头
@RequiredArgsConstructor      // 依赖注入
public class UserController {

    private final UserAuthService userAuthService;  // 注入业务服务
```

`@RestController` = `@Controller` + `@ResponseBody`（返回值自动序列化成 JSON）。

≈ Next.js App Router：
```ts
// app/api/user/login/route.ts
export async function POST(request: Request) { ... }
```

### register 接口

```java
@PostMapping("/register")   // POST /user/register
public ApiResponse<UserProfile> register(
    @Valid                    // 触发参数校验
    @RequestBody             // 把 JSON body 反序列化成 Java 对象
    RegisterRequest request
) {
    return ApiResponse.success(userAuthService.register(request));
}
```

前端发 → Spring 自动转换：

```json
POST /user/register
{ "username": "test", "password": "123456" }
```
↓
```java
RegisterRequest { username="test", password="123456" }
```

如果 `@Valid` 校验失败（比如 username 为空），抛 `MethodArgumentNotValidException`。

### login 接口

```java
@PostMapping("/login")   // POST /user/login
public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(userAuthService.login(request));
}
```

### me 接口

```java
@GetMapping("/me")   // GET /user/me
public ApiResponse<UserProfile> me(
    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
) {
```

`@RequestHeader` = 从 HTTP 请求头取值。

前端请求时：`headers: { Authorization: "Bearer eyJhbG..." }`

`required = false` = 头不是必须的（未登录也能调）。

```java
    if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
        return ApiResponse.fail("未登录");
    }
    String token = authorization.substring("Bearer ".length());  // 去掉 "Bearer " 前缀
    return ApiResponse.success(userAuthService.me(token));
```

---

## 11. 全局错误处理 — controller/GlobalExceptionHandler.java

```java
@RestControllerAdvice   // 拦截所有 Controller 的异常
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)  // 业务异常
    public ApiResponse<Void> handleBusiness(IllegalArgumentException ex) {
        return ApiResponse.fail(ex.getMessage());
        // → { code: 1, message: "用户名已存在", data: null }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)  // 校验失败
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().isEmpty()
            ? "参数校验失败"
            : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ApiResponse.fail(msg);
        // → { code: 1, message: "用户名不能为空", data: null }
    }

    @ExceptionHandler(JwtException.class)  // Token 过期/被篡改
    public ApiResponse<Void> handleJwt(JwtException ex) {
        return ApiResponse.fail("登录已过期，请重新登录");
    }

    @ExceptionHandler(Exception.class)  // 兜底：所有未知异常
    public ApiResponse<Void> handleOther(Exception ex) {
        return ApiResponse.fail("系统异常: " + ex.getMessage());
    }
}
```

**没有这个类**，`throw new IllegalArgumentException("用户名已存在")` 会导致 Spring 返回一个难看的 HTML 500 错误页。

≈ 前端：

```ts
try {
    await store.login({ username, password });
} catch (err) {
    setError(err instanceof Error ? err.message : "登录失败");
}
```

---

## 12. 配置文件 — application.yml

```yaml
server:
  port: 7101                    # 服务端口号 ≈ 前端 PORT=3000

spring:
  application:
    name: hc-user               # 服务名，注册到 Nacos 用

  datasource:                    # 数据库连接 ≈ 前端 DATABASE_URL
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://${MYSQL_HOST:mysql}:3306/hc_user?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5           # 初始连接数
      min-idle: 5               # 最小空闲连接
      max-active: 20            # 最大活跃连接
      max-wait: 3000            # 获取连接最大等待时间(ms)

  data:
    redis:                       # Redis 连接配置
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}

  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:nacos:8848}
        username: ${NACOS_USERNAME:nacos}
        password: ${NACOS_PASSWORD:nacos}

jwt:                             # JWT 配置
  secret: ${JWT_SECRET:mySecretKeyForJwtTokenGenerationMustBe256BitsLong2024!}
  expiration: ${JWT_EXPIRATION:86400000}  # 24小时

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 打印 SQL（开发用）
  global-config:
    db-config:
      logic-delete-field: deleted       # 逻辑删除字段
      logic-delete-value: 1             # 删除=1
      logic-not-delete-value: 0         # 未删除=0
```

**`${MYSQL_HOST:mysql}` 语法解释：**

```
${环境变量名:默认值}
```

- Docker 里：没设环境变量 → 用默认值 `mysql`（Docker 内部域名）
- 本地跑：设 `MYSQL_HOST=localhost` → 用 `localhost`

≈ 前端 `process.env.NEXT_PUBLIC_API_BASE || "http://localhost:7100"`

**逻辑删除**：不真删数据，只把 `deleted` 改成 1。查询自动过滤 `deleted=1` 的记录。

---

## 13. 完整请求流程

### 注册流程

```
1. 前端：用户填写注册表单，点"注册"
2. 前端：store.register({ username, password, phone, nickname })
3. 前端：fetch POST /api/user/register { body: JSON }
4. Next.js rewrite：/api/user/register → gateway:7100/api/user/register
5. Gateway：StripPrefix → user:7101/user/register
6. Spring：@RequestBody 把 JSON → RegisterRequest 对象
7. Spring：@Valid 校验（@NotBlank、@Size 等）
8. Controller：调用 userAuthService.register(request)
9. Service：查重 → 加密密码 → 写入 MySQL
10. Service：User → UserProfile（脱敏）
11. Controller：ApiResponse.success(profile)
12. Spring：自动序列化 → JSON 返回
13. Gateway：转发给前端
14. 前端：注册成功，跳转登录页
```

### 登录流程

```
1. 前端：用户填写登录表单，点"登录"
2. 前端：store.login({ username, password })
3. 前端：fetch POST /api/user/login { body: JSON }
4. Next.js rewrite → Gateway → User 服务
5. Service：查用户 → BCrypt 验密 → 生成 JWT
6. 返回：{ code: 0, data: { token: "eyJ...", user: {...} } }
7. 前端：set({ token, user }) → localStorage 持久化
8. 前端：router.push("/") → 首页显示"你好，xxx"
```

### 获取当前用户流程

```
1. 前端：页面刷新，token 在 localStorage，user 为 null
2. 前端：useEffect 检测到 token && !user → fetchMe()
3. 前端：fetch GET /api/user/me { headers: { Authorization: "Bearer xxx" } }
4. Gateway → User 服务 /user/me
5. Controller：取出 token，去掉 "Bearer " 前缀
6. Service：JwtUtil 解析 token → userId → 查数据库
7. 返回：{ code: 0, data: { id, username, nickname, ... } }
8. 前端：set({ user }) → 首页显示用户信息
```

### 错误流程

```
1. 前端：用户输入错误密码
2. Service：BCrypt 验证失败 → throw new IllegalArgumentException("用户名或密码错误")
3. GlobalExceptionHandler：拦截异常 → ApiResponse.fail("用户名或密码错误")
4. 返回：{ code: 1, message: "用户名或密码错误", data: null }
5. 前端：catch (err) → setError(err.message) → 页面显示红色错误提示
```

---

## 14. Java vs 前端概念对照表

| Java | 前端 | 说明 |
|---|---|---|
| `@RestController` + `@PostMapping` | `app/api/route.ts` + `export POST` | 定义 API 路由 |
| `@Service` | Zustand store / hooks | 业务逻辑层 |
| `@Mapper` + `BaseMapper` | Prisma Client | 数据库操作 |
| `@Data` (Lombok) | TypeScript `type` + 自动推断 | 数据结构定义 |
| `@Builder` (Lombok) | 对象字面量 `{ ... }` | 方便构造对象 |
| `@Valid` | Zod / 手动 if 校验 | 参数校验 |
| `@Value("${x:默认}")` | `process.env.X \|\| 默认值` | 读取配置 |
| `@Component` / `@Service` | zustand `create()` | 注册到容器/store |
| `@RequiredArgsConstructor` + `private final` | `const store = useStore()` | 依赖注入 |
| `throw new Exception` | `throw new Error()` | 异常中断 |
| `@RestControllerAdvice` | 全局 try/catch 兜底 | 统一错误处理 |
| `@ExceptionHandler` | `catch (err)` 子句 | 捕获特定异常 |
| `private` | 闭包 / `#` 私有字段 | 访问控制 |
| `static` 方法 | 模块级函数 / `Math.random()` | 不需要实例就能调 |
| 泛型 `<T>` | TypeScript 泛型 `<T>` | 类型参数化 |
| `interface` | TypeScript `interface` | 定义方法/类型签名 |
| `extends` | `extends` / `implements` | 继承/实现 |
| `new` | `new` / `{}` | 创建对象 |
| `application.yml` | `.env` / `next.config.js` | 配置文件 |
| `package com.hc` | 目录路径 `src/` | 命名空间 |
| `import com.hc.dto.X` | `import { X } from "@/dto"` | 导入 |
