# Java 分层架构 & DTO 详解 — 前端开发者视角

> 本文档专为前端开发者编写，解释 Java 后端的分层架构（MVC 演进）和 DTO 的作用，用前端概念对照说明。

---

## 目录

1. [从 MVC 到分层架构](#1-从-mvc-到分层架构)
2. [User 服务的分层体现](#2-user-服务的分层体现)
3. [为什么要分层](#3-为什么要分层)
4. [DTO 是什么](#4-dto-是什么)
5. [Request DTO vs Response DTO](#5-request-dto-vs-response-dto)
6. [Model vs DTO 的区别](#6-model-vs-dto-的区别)
7. [数据流转全景图](#7-数据流转全景图)
8. [前端类比总结](#8-前端类比总结)

---

## 1. 从 MVC 到分层架构

### 经典 MVC

MVC 是 1970 年代提出的架构模式：

```
M (Model)      → 数据 + 业务逻辑
V (View)       → 界面展示
C (Controller) → 调度，连接 M 和 V
```

原始设计是给**桌面应用**用的（同一个进程里有界面和数据）。

### Web 时代的演变

到了 Web 时代，"View" 变成了浏览器（前端），后端不再渲染界面，MVC 的含义变了：

```
经典 MVC（桌面应用）          现代 Web 分层架构
┌──────────────┐             ┌──────────────┐
│  View (界面)  │             │  前端 (React) │  ← View 跑到浏览器了
│  Controller   │             │  Controller   │  ← 只管接请求、返响应
│  Model (数据)  │             │  Service      │  ← 业务逻辑从 Controller 分出来
│                              │  Repository   │  ← 数据库操作从 Model 分出来
│                              │  Model        │  ← 只管数据结构
│                              │  DTO          │  ← 新增：请求/响应的数据格式
└──────────────┘             └──────────────┘
```

**关键区别：**
- 经典 MVC 的 Model 既管数据又管逻辑 → 现在拆成了 Service + Repository + Model
- 经典 MVC 的 View 是后端渲染的 HTML → 现在 View 是前端 React/Next.js
- 新增了 DTO 层 → 控制前后端之间传数据的格式

### 这个项目的实际分层

```
┌─────────────────────────────────────────────────────┐
│  前端 (Next.js)                                      │
│  fetch → /api/user/login → 收到 JSON → 更新 store    │
└────────────────────┬────────────────────────────────┘
                     │ HTTP 请求/响应
┌────────────────────▼────────────────────────────────┐
│  Controller (UserController.java)                   │
│  接收请求、调用 Service、返回 DTO                      │
└────────────────────┬────────────────────────────────┘
                     │ 方法调用
┌────────────────────▼────────────────────────────────┐
│  Service (UserAuthService.java)                     │
│  业务逻辑：查重、加密、生成 token                       │
└────────────────────┬────────────────────────────────┘
                     │ 方法调用
┌────────────────────▼────────────────────────────────┐
│  Repository (UserMapper.java)                       │
│  数据库操作：select、insert                           │
└────────────────────┬────────────────────────────────┘
                     │ SQL
┌────────────────────▼────────────────────────────────┐
│  MySQL (t_user 表)                                   │
└─────────────────────────────────────────────────────┘
```

---

## 2. User 服务的分层体现

| 层 | 文件 | 职责 | 代码行数 | 前端类比 |
|---|---|---|---|---|
| **Controller** | `UserController.java` | 接收 HTTP 请求，调用 Service，返回 JSON | ~50 行 | `app/api/user/route.ts` |
| **Service** | `UserAuthService.java` | 业务逻辑（查重、加密、生成 token） | ~94 行 | Zustand store 的核心逻辑 |
| **Repository** | `UserMapper.java` | 数据库 CRUD | ~3 行 | Prisma Client |
| **Model** | `User.java` | 数据库表结构映射 | ~20 行 | TypeScript `type User` |
| **Request DTO** | `LoginRequest.java` 等 | 前端传什么参数 | ~15 行 | `type LoginRequest = { ... }` |
| **Response DTO** | `UserProfile.java` 等 | 后端返回什么数据 | ~25 行 | `type LoginResponse = { ... }` |
| **Util** | `JwtUtil.java` | 工具方法 | ~56 行 | `lib/jwt.ts` |
| **异常处理** | `GlobalExceptionHandler.java` | 统一错误返回 | ~34 行 | 全局 try/catch |

### Controller 层 — 只管接单派活

```java
@PostMapping("/login")
public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.success(userAuthService.login(request));
    //                     ↑ 全丢给 Service，自己不干活
}
```

**Controller 不应该有业务逻辑**。它只做三件事：
1. 接收请求（`@RequestBody` 把 JSON → Java 对象）
2. 调用 Service（`userAuthService.login(request)`）
3. 返回响应（`ApiResponse.success(...)`）

≈ 前端 API Route：
```ts
export async function POST(request: Request) {
    const body = await request.json();           // 1. 接收
    const result = await authStore.login(body);   // 2. 调用逻辑
    return Response.json(result);                  // 3. 返回
}
```

### Service 层 — 真正干活的人

```java
public LoginResponse login(LoginRequest request) {
    // 1. 查数据库
    User user = userMapper.selectOne(...);
    // 2. 验证密码
    if (!passwordEncoder.matches(...)) throw ...;
    // 3. 生成 token
    String token = jwtUtil.generateToken(...);
    // 4. 组装返回
    return LoginResponse.builder().token(token).user(toProfile(user)).build();
}
```

**Service 才是业务逻辑的核心**。它可以调用多个 Repository、Util，完成复杂的业务流程。

≈ 前端 Zustand store：
```ts
login: async (username, password) => {
    const user = await prisma.user.findFirst({ where: { username } });
    if (!user || !bcrypt.compare(password, user.password)) throw new Error("错误");
    const token = jwt.sign({ userId: user.id }, secret);
    return { token, user: toProfile(user) };
}
```

### Repository 层 — 只管数据库

```java
public interface UserMapper extends BaseMapper<User> {
    // 空的！CRUD 方法 MyBatis-Plus 全帮你生成了
}
```

**Repository 不知道业务逻辑**，只提供数据操作能力。

≈ 前端 Prisma Client：它只管查/插/改/删，不管业务。

---

## 3. 为什么要分层

### 不分层的灾难

```java
// ❌ 全挤在 Controller 里，200 行一个方法
@PostMapping("/register")
public ApiResponse<UserProfile> register(@RequestBody RegisterRequest request) {
    // 查数据库
    User existing = userMapper.selectOne(
        new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())
    );
    if (existing != null) {
        return ApiResponse.fail("用户名已存在");
    }
    // 加密密码
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    String encoded = encoder.encode(request.getPassword());
    // 创建对象
    User user = new User();
    user.setUsername(request.getUsername());
    user.setPassword(encoded);
    user.setPhone(request.getPhone());
    user.setEmail(request.getEmail());
    user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
    user.setStatus(1);
    // 插入数据库
    userMapper.insert(user);
    // 转换
    UserProfile profile = new UserProfile();
    profile.setId(user.getId());
    profile.setUsername(user.getUsername());
    profile.setPhone(user.getPhone());
    profile.setEmail(user.getEmail());
    profile.setNickname(user.getNickname());
    profile.setAvatar(user.getAvatar());
    profile.setStatus(user.getStatus());
    return ApiResponse.success(profile);
}
```

### 分层后

```java
// ✅ Controller：1 行搞定
@PostMapping("/register")
public ApiResponse<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.success(userAuthService.register(request));
}
```

### 分层的好处

| 好处 | 说明 | 例子 |
|---|---|---|
| **职责单一** | 每层只做自己的事 | Controller 不管数据库，Service 不管 HTTP |
| **可复用** | 同一个 Service 方法可以被多个 Controller 调用 | 管理员注册和用户注册可以共用 Service |
| **可测试** | 可以单独测 Service，不用启动 HTTP 服务 | 单元测试直接 `new UserAuthService()` |
| **可替换** | 换数据库只改 Repository，其他不用动 | MySQL → PostgreSQL，只改 Mapper |
| **好维护** | 改一个层不影响其他层 | 加个字段只改 Model 和 DTO |

---

## 4. DTO 是什么

**DTO = Data Transfer Object = 数据传输对象**

它是**前后端之间传递数据的格式定义**，和数据库里存的格式不一定一样。

### 核心作用：控制"什么数据该给谁看"

```
数据库里的 User              前端看到的 UserProfile
(全量，包含敏感信息)          (脱敏，只给前端该看的)
┌──────────────────┐        ┌──────────────────┐
│ id               │        │ id               │
│ username         │        │ username         │
│ password  ← 敏感 │        │ phone            │ ← 没有 password
│ phone            │        │ email            │ ← 没有 createTime
│ email            │        │ nickname         │ ← 没有 updateTime
│ nickname         │        │ avatar           │
│ avatar           │        │ status           │
│ status           │        └──────────────────┘
│ createTime ← 不需要│
│ updateTime ← 不需要│
└──────────────────┘
```

### 没有 DTO 的后果

**场景1：密码泄露**

```java
// ❌ 直接返回数据库的 User
public User login(LoginRequest request) { return userMapper.selectOne(...); }
// 前端收到：{ "password": "$2a$10$N9qo8uLOickgx2ZMRZoMy..." }
// 密码泄露！即使加密了也不该给前端
```

**场景2：前端传了不该传的字段**

```java
// ❌ 直接接收 User 对象
@PostMapping("/register")
public void register(@RequestBody User user) { userMapper.insert(user); }
// 黑客可以传：{ "username": "admin", "status": 1, "id": 1 }
// 直接操控数据库字段！
```

**场景3：字段不匹配**

前端登录需要返回 `token`，但数据库的 `User` 表没有 `token` 字段。
没有 DTO，怎么返回 token？

### 有 DTO 之后

```java
// ✅ 用 RegisterRequest 接收（只有前端该传的字段）
@PostMapping("/register")
public ApiResponse<UserProfile> register(@Valid @RequestBody RegisterRequest request) {
    // request 只有 username、password、phone、email、nickname
    // 没有id、status、createTime — 黑客传了也会被忽略
}

// ✅ 用 LoginResponse 返回（包含 token + 脱敏用户信息）
public LoginResponse login(LoginRequest request) {
    String token = jwtUtil.generateToken(...);
    return LoginResponse.builder().token(token).user(toProfile(user)).build();
    // 返回 { token: "eyJ...", user: { id, username, ... } }
    // 没有 password
}
```

---

## 5. Request DTO vs Response DTO

### Request DTO — 前端传给后端的格式

| DTO | 字段 | 用途 |
|---|---|---|
| `RegisterRequest` | username, password, phone, email, nickname | 注册表单提交 |
| `LoginRequest` | username, password | 登录表单提交 |

**作用：限制前端只能传这些字段，防止提交不该提交的数据。**

前端类比：
```ts
// 前端的 RegisterRequest ≈ 表单数据的类型定义
type RegisterRequest = {
    username: string;    // 必填
    password: string;    // 必填
    phone?: string;      // 可选
    email?: string;      // 可选
    nickname?: string;   // 可选
    // 没有 id、status、createTime — 这些不该由前端决定
}
```

**注解校验也加在 Request DTO 上：**

```java
@NotBlank(message = "用户名不能为空")    // ≈ 前端的 if (!username)
@Size(min = 4, max = 20)               // ≈ 前端的 if (username.length < 4)
private String username;
```

### Response DTO — 后端返回给前端的格式

| DTO | 字段 | 用途 |
|---|---|---|
| `LoginResponse` | token, user (UserProfile) | 登录成功返回 |
| `UserProfile` | id, username, phone, email, nickname, avatar, status | 用户信息（脱敏） |
| `ApiResponse<T>` | code, message, data | 所有接口的通用包装 |

**作用：控制返回给前端的数据，去掉敏感字段、加上计算字段。**

前端类比：
```ts
// 前端的 UserProfile ≈ API 响应的类型定义
type UserProfile = {
    id: number;
    username: string;
    phone: string | null;
    email: string | null;
    nickname: string;
    avatar: string | null;
    status: number;
    // 没有 password！
}
```

---

## 6. Model vs DTO 的区别

**最容易混淆的概念：Model 和 DTO 都是数据类，但用途完全不同。**

| | Model (User.java) | DTO (UserProfile.java) |
|---|---|---|
| **用途** | 映射数据库表 | 前后端传数据的格式 |
| **对应** | 一条 SQL 记录 | 一个 API 响应 |
| **password** | ✅ 有（数据库里存着） | ❌ 没有（不该给前端） |
| **createTime** | ✅ 有 | ❌ 没有（前端不需要） |
| **token** | ❌ 没有（数据库没这列） | ✅ 有（LoginResponse 里有） |
| **注解** | `@TableName`, `@TableId` | `@Builder`, `@Data` |
| **谁用** | Repository / Service | Controller / 前端 |

### 关系图

```
数据库                Model              DTO              前端
t_user 表    →     User.java     →   UserProfile.java  →  TypeScript type
(10列)             (10字段)           (7字段)             (7字段)
                    ↑ 全量              ↑ 脱敏              ↑ 一样
                    有 password         没有 password       没有 password
```

### 转换方法

Service 里用 `toProfile()` 做转换：

```java
private UserProfile toProfile(User user) {
    return UserProfile.builder()
        .id(user.getId())              // ✅ 保留
        .username(user.getUsername()) // ✅ 保留
        .phone(user.getPhone())       // ✅ 保留
        .email(user.getEmail())       // ✅ 保留
        .nickname(user.getNickname()) // ✅ 保留
        .avatar(user.getAvatar())     // ✅ 保留
        .status(user.getStatus())     // ✅ 保留
        // password    ← 故意不拷贝！
        // createTime  ← 故意不拷贝！
        // updateTime  ← 故意不拷贝！
        .build();
}
```

---

## 7. 数据流转全景图

### 注册流程

```
前端                    Controller               Service                 Repository          数据库
────                    ──────────               ───────                 ──────────          ──────
POST /user/register
{ username,             RegisterRequest          UserAuthService         UserMapper          t_user
  password,             (DTO - 入)              .register()             .selectOne()        SELECT
  phone,                   ↓                       ↓                     .insert()          INSERT
  email,               @Valid 校验             查重 + 加密
  nickname }               ↓                       ↓
                       调用 Service           User → UserProfile
                           ↓                   (Model → DTO)
                       ApiResponse              ↓
                       <UserProfile>         返回 LoginResponse
                           ↓                       ↓
                       前端收到 JSON         UserProfile (DTO - 出)
                       { code, message,
                         data: { id, username,
                                 phone, ... } }
```

### 登录流程

```
前端                    Controller               Service                 Repository          数据库
────                    ──────────               ───────                 ──────────          ──────
POST /user/login
{ username,             LoginRequest             UserAuthService         UserMapper          t_user
  password }            (DTO - 入)              .login()                .selectOne()        SELECT
                            ↓                       ↓
                        @Valid 校验            验证密码
                            ↓                       ↓
                        调用 Service           生成 JWT token
                            ↓                       ↓
                        ApiResponse          User → UserProfile
                        <LoginResponse>      (Model → DTO)
                            ↓
                        前端收到 JSON
                        { code: 0,
                          data: {
                            token: "eyJ...",
                            user: { id, username, ... }
                          } }
```

### 完整的类依赖关系

```
UserController
  ├── 接收: RegisterRequest, LoginRequest    (Request DTO)
  ├── 调用: UserAuthService                   (Service)
  └── 返回: ApiResponse<UserProfile>          (Response DTO)
              ApiResponse<LoginResponse>       (Response DTO)
                        │
UserAuthService
  ├── 注入: UserMapper                        (Repository)
  ├── 注入: JwtUtil                            (Util)
  ├── 读取: User                              (Model)
  └── 返回: UserProfile, LoginResponse        (Response DTO)

UserMapper
  └── 操作: User                              (Model)
```

---

## 8. 前端类比总结

| Java 概念 | 前端类比 | 说明 |
|---|---|---|
| **Controller** | `app/api/route.ts` | 接请求、返响应，不写逻辑 |
| **Service** | Zustand store 核心逻辑 | 干活的，包含业务规则 |
| **Repository** | Prisma Client | 只管数据库 CRUD |
| **Model** | `type User = { ... }` + Prisma schema | 数据库表结构映射 |
| **Request DTO** | 表单提交的类型定义 | 限制前端传什么 |
| **Response DTO** | API 响应的类型定义 | 控制前端看到什么 |
| **Model → DTO 转换** | `toProfile()` 函数 | 脱敏，去掉 password |
| **@Valid** | Zod schema 验证 | 自动校验参数 |
| **GlobalExceptionHandler** | 全局 try/catch | 统一错误返回格式 |

### 核心原则

1. **Controller 不写逻辑** — 只接单派活
2. **Service 不管 HTTP** — 只关心业务规则
3. **Repository 不知道业务** — 只管数据库操作
4. **Model 是数据库的映射** — 全量字段，含敏感信息
5. **DTO 是前后端的契约** — 脱敏、校验、格式化
6. **永远不直接返回 Model** — 通过 DTO 转换后再返回
