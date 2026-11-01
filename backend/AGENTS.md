# 后端工程提示词

> 适用目录：`D:\mdreader\backend`
> 整理日期：2026-10-28
> 更新日期：2026-10-30
> 用途：作为后续 AI / 开发者修改本后端时的工程约定与上下文说明。

---

## 1. 角色与目标

你是本项目 Markdown Web 阅读器的后端工程师。后端负责：

- 用户与文件元数据查询。
- Markdown 文件及图片资源的持久化与访问。
- 为前端提供稳定的 JSON 接口。
- 提供 JWT 登录/鉴权、当前用户文件列表等接口，并为文件上传、审核/发布流程保留清晰的分层结构。

修改代码前先阅读现有 Controller、Service、Mapper、XML 和 `docs/api/backend-api-v1.0.md`，保持现有风格，不要另起一套架构。

---

## 2. 技术栈与硬约束

### 2.1 当前技术栈

| 组件 | 版本 / 说明 |
|---|---|
| Spring Boot | `3.5.3` |
| Java | 源码目标 `21`；本机编译使用 JDK 23，`--release 21` |
| MyBatis | `mybatis-spring-boot-starter 3.0.5` |
| MySQL | `8.0.34` |
| Flyway | `flyway-core` / `flyway-mysql 11.7.2` |
| JWT | `jjwt 0.12.6`（登录、鉴权拦截器已接入） |
| Lombok | 已启用，版本由 Spring Boot 管理 |
| Maven | `3.9.4` |

### 2.2 依赖约束

- 不擅自新增清单外依赖。确需新增时先说明用途、替代方案和影响。
- 当前没有引入 `spring-boot-starter-validation`、Spring Security、Swagger/OpenAPI、MapStruct、Hutool 等。
- 参数校验当前以 Service 内手动判断为主，不为了简单校验引入新框架。
- Lombok 仅用于减少样板代码，不引入 Lombok 之外的对象映射工具。

### 2.3 环境变量

生产/非本地环境通过环境变量注入：

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
JWT_SECRET
JWT_EXPIRATION_MINUTES
CORS_ALLOWED_ORIGINS
SERVER_PORT
SPRING_PROFILES_ACTIVE
```

不要把真实密码、JWT 密钥、数据库连接串硬编码到代码或提交到仓库。

---

## 3. 目录与分层

```text
src/main/java/com/mdreader/
├── MdReaderApplication.java
├── common/
│   ├── Result.java
│   ├── PageResult.java
│   ├── constant/SystemConstants.java
│   └── exception/
│       ├── BusinessException.java
│       └── GlobalExceptionHandler.java
├── config/                 # WebMvc、CORS、Jackson、拦截器注册等配置
├── controller/             # 只做参数接收和响应包装
├── entity/
│   ├── entity/             # DO，对应数据库表
│   ├── dto/                # 查询/写入入参
│   └── vo/                 # 接口出参
├── interceptor/            # JWT 鉴权拦截器
├── mapper/                 # MyBatis Mapper 接口
├── service/
│   ├── UserService.java
│   ├── FileService.java
│   ├── AuthService.java
│   └── impl/               # Service 实现
└── utils/
    ├── JwtUtil.java
    ├── PasswordUtil.java
    └── PageUtil.java
```

资源目录：

```text
src/main/resources/
├── application.yml
├── application-local.yml
├── application-prod.yml
├── db/migration/           # Flyway 迁移脚本
└── mapper/                 # MyBatis XML
```

分层职责：

- **Controller**：接收参数、调用 Service、返回 `Result<T>`；不写业务逻辑和 SQL。
- **Service**：业务规则、分页归一化、DO → VO 转换、异常抛出。
- **Mapper**：只负责数据访问；SQL 写在 XML 中。
- **DO / DTO / VO**：只承载数据，不掺杂业务逻辑。
- **XML**：动态 SQL；不要写大段 `resultMap`。

---

## 4. 编码风格

### 4.1 构造器注入

所有 Spring Bean 使用 `@RequiredArgsConstructor` + `private final` 构造器注入：

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
}
```

禁止：

- 字段注入（`@Autowired` 直接标注字段）。
- 手写无必要的构造器。
- 在 Controller 中直接注入 Mapper。

### 4.2 Lombok

DO / DTO / VO 统一使用：

```java
@Getter
@Setter
public class FileDO {
    // ...
}
```

- 不使用 `@Data` 替代明确的 `@Getter` / `@Setter`，避免无意生成 `equals/hashCode/toString`。
- `Result` 使用 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`。
- `PageResult` 使用 `@Getter @Setter @NoArgsConstructor`，保留计算总页数的自定义构造器。
- Lombok 注解处理器已在 `pom.xml` 的 `maven-compiler-plugin` 中显式配置，原因是 JDK 23 默认不执行隐式注解处理；不要删除该配置。

### 4.3 字段必须写用途注释

每个实体字段都要有中文 JavaDoc，说明业务含义、枚举取值或单位：

```java
/**
 * 文件标题（用户可见名称）
 */
private String title;

/**
 * 文件大小（字节）
 */
private Long fileSize;

/**
 * 可见性：PUBLIC / PRIVATE
 */
private String visibility;
```

不要只复述字段名。例如 `userId` 应写“所属用户 ID”，而不是“用户 ID”。

### 4.4 方法必须写用途与策略

Service、Service 实现、Mapper 接口的方法都要有 JavaDoc，至少包含：

- 用途。
- `@param`。
- `@return`。
- 策略：简述实现方式、SQL 条件、分页方式或异常策略。

示例：

```java
/**
 * 根据 ID 查询文件详情。
 *
 * @param id 文件 ID
 * @return 文件详情
 * 策略：仅查询 visibility=PUBLIC 且 status=PUBLISHED 的记录，并解析 img_urls JSON
 */
```

Mapper 与 Service 的返回语义要区分：

- Mapper 查询不到时返回 `null`。
- Service 查询不到时抛出 `BusinessException(404, "...")`。

简单方法也要有一句话说明，不要为了注释而写空话。

### 4.5 命名

- 数据库字段：`snake_case`。
- Java 字段：`camelCase`。
- 实体后缀：DO / DTO / VO。
- Service 方法：
  - `getById`
  - `page`
  - `pagePublic`
  - `pageByUserId`
- Mapper 方法：
  - `selectById`
  - `countPage`
  - `selectPage`
  - `selectPublicById`
  - `countPublic`
  - `selectPublicPage`
  - `countByUserId`
  - `selectByUserId`

命名要表达“查什么、按什么条件”，不要使用 `getData`、`queryList` 这类模糊名称。

---

## 5. 接口与返回结构

### 5.1 统一响应

所有接口返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- `Result.success(data)`：成功。
- `Result.fail(code, message)`：失败，`data` 固定为 `null`。
- `code` 与 HTTP 状态码保持一致。
- 不返回异常堆栈、SQL、内部路径或敏感字段。

### 5.2 分页

分页统一使用 `PageUtil`：

- `pageNum` 默认 `1`，小于 1 时按 1 处理。
- `pageSize` 默认 `10`，小于 1 时按 10 处理，最大 `100`。
- 返回 `PageResult`：`records`、`total`、`pageNum`、`pageSize`、`pages`。
- 无数据时 `records` 必须是 `[]`，不能是 `null`。
- 总页数使用 `(total + pageSize - 1) / pageSize` 向上取整。
- 具体接口契约可以定义更严格的分页上限与校验策略（例如当前用户文件列表 `size` 上限 `20`，非法值返回 `400`）；此时以契约为准，不要直接套用 `PageUtil` 的自动归一化。
- 当前用户文件列表按契约返回 `{ total, records }`，使用 `CurrentUserFileListVO`，不使用 `PageResult`。

### 5.3 异常

业务异常统一抛出：

```java
throw new BusinessException(404, "文件不存在或未公开");
```

由 `GlobalExceptionHandler` 转换为：

```json
{
  "code": 404,
  "message": "文件不存在或未公开",
  "data": null
}
```

- 参数类型错误返回 `400`，如 `参数格式错误：id`。
- 未登录、Token 缺失/无效/过期返回 `401`。
- 角色不足返回 `403`，如普通用户访问 `/api/admin/**`。
- 未处理异常返回 `500`，对外只暴露“服务器内部错误”。
- 不要在 Controller 中手写 `try/catch` 包装通用异常。

### 5.4 时间与 ID

- 时间字段使用 `LocalDateTime` / `DATETIME(3)`。
- JSON 时间统一为 `yyyy-MM-dd HH:mm:ss`，如 `2026-10-28 15:54:02`，不带时区；由 `JacksonConfig` 全局配置。
- 主键为 `BIGINT UNSIGNED`，当前以 JSON `number` 返回。
- 若未来 ID 超过 JS 安全整数范围，需要统一改为字符串序列化，不能只改单个接口。

---

## 6. MyBatis 规范

### 6.1 全局驼峰映射

`application.yml` 已配置：

```yaml
mybatis:
  configuration:
    map-underscore-to-camel-case: true
```

SQL 列名使用 `snake_case`，Java 字段使用 `camelCase`，由 MyBatis 自动映射。

### 6.2 禁止大段 resultMap

查询直接使用 `resultType`：

```xml
<select id="selectById" resultType="com.mdreader.entity.entity.UserDO">
    SELECT id, username, email, role, status, created_at, updated_at
    FROM `user`
    WHERE id = #{id}
</select>
```

不要重新引入逐字段 `resultMap`，除非出现自动映射无法处理的特殊类型转换；引入前必须说明原因。

### 6.3 XML 组织

- Mapper 接口在 `com.mdreader.mapper`。
- XML 在 `src/main/resources/mapper/`。
- 公共列使用 `<sql id="...">` + `<include>`。
- 动态条件使用 `<where>` / `<if>`。
- 分页统一使用 `LIMIT #{offset}, #{limit}`。
- 多参数 Mapper 方法必须使用 `@Param`。
- 不在注解中写复杂 SQL，不把 SQL 拼接到 Java 字符串中。

### 6.4 查询语义

当前已实现：

| 接口 | 条件 | 排序 |
|---|---|---|
| 用户详情 | `user.id = ?` | 无 |
| 用户分页 | 可选用户名/邮箱模糊、角色、状态 | `created_at DESC` |
| 文件详情 | `id = ?`；非所有者额外要求 `visibility = 'PUBLIC' AND status = 'PUBLISHED'` | 无 |
| 文件正文 | `id = ?`；权限同文件详情 | 无 |
| 公开文件分页 | `visibility = 'PUBLIC' AND status = 'PUBLISHED'`，可选标题模糊 | `created_at DESC` |
| 当前用户文件列表 | `user_id = 当前 Token 用户`，不限制可见性和状态 | `updated_at DESC, id DESC` |
| 管理员文件分页 | 可选 `user_id/visibility/status/keyword` | `updated_at DESC, id DESC` |

不要在未确认需求前改变公开文件的筛选规则。

---

## 7. 数据库与 Flyway

### 7.1 基本约定

- 数据库：`mdreader`，字符集 `utf8mb4`。
- 时间字段：`DATETIME(3)`，默认值为 `CURRENT_TIMESTAMP(3)`。
- 主键：`BIGINT UNSIGNED AUTO_INCREMENT`。
- 表名/字段名使用反引号包裹保留字（如 `` `user` ``）。
- 状态类字段使用明确字符串枚举；数据库层尽量加 `CHECK` 约束。
- 外键删除策略明确：当前 `files.user_id` 为 `ON DELETE RESTRICT`。

### 7.2 Flyway

- 建表、索引、约束、种子数据全部通过 Flyway 迁移完成。
- 迁移文件命名：`V{序号}__{snake_case_描述}.sql`，例如 `V3__add_file_tags.sql`。
- 不要修改已经在共享环境执行过的迁移文件；新增变更必须新建 `V2`、`V3`。
- `spring.flyway.baseline-on-migrate` 当前为 `false`；首次启动前数据库应为空库。
- 迁移记录表：`flyway_schema_history`。

### 7.3 当前表结构

`user`：

- `id`、`username`、`nickname`、`avatar`、`password_hash`、`email`、`role`、`status`、`token_version`、`created_at`、`updated_at`。
- `role`：`ADMIN` / `USER`。
- `status`：`1` 启用，`0` 禁用。
- `token_version` 由 Flyway `V5` 新增，改密、禁用、降权时递增。
- `must_change_password` 由 Flyway `V8` 新增，`1` 表示必须先修改密码。
- `username` 唯一，`email` 唯一且可空。
- `nickname`、`avatar` 由 Flyway `V3` 新增，均可空。

`files`：

- `id`、`user_id`、`parent_id`、`parent_key`、`node_type`、`title`、`original_name`、`content`、`md_url`、`md_object_key`、`storage_provider`、`img_urls`、`file_size`、`visibility`、`status`、`created_at`、`updated_at`。
- `parent_id` / `node_type` 由 Flyway `V4` 新增；`parent_key` 由 `V6` 新增为 `IFNULL(parent_id, 0)` 存储生成列，与 `user_id`、`title` 组成同级唯一索引。
- `content` 由 Flyway `V3` 新增，`LONGTEXT NULL`；内部创建的正文存储在此字段，`md_url` 固定为 `/api/files/{id}/content`。
- `visibility`：`PUBLIC` / `PRIVATE`。
- `status`：`DRAFT` / `PENDING` / `PUBLISHED` / `REJECTED`。
- `storage_provider`：`ALIYUN_OSS` / `TENCENT_COS` / `S3` / `LOCAL`（数据库未加 `CHECK`）。
- `img_urls` 是 JSON 对象数组，不是字符串数组：

```json
[
  {
    "url": "https://example.com/a.png",
    "objectKey": "img/a.png",
    "provider": "ALIYUN_OSS",
    "alt": "示例图"
  }
]
```

- 不存在 `reviewed_by` / `reviewed_at` / `reject_reason` 字段；不要擅自补回。
- `md_object_key` 仅后端内部使用，不通过接口返回。

### 7.4 种子数据

Flyway `V1` 插入管理员，`V2` 将初始密码更新为 PBKDF2-HMAC-SHA256 哈希：

```text
username: admin
initial password: 1234
role: ADMIN
status: 1
```

`V2__hash_admin_password.sql` 已把 `password_hash` 更新为 `pbkdf2_sha256$迭代次数$盐$哈希`。生产环境仍应替换默认管理员密码；不要修改已执行过的 `V1/V2`，后续变更新增迁移。

---

## 8. 当前业务规则

- 文件详情 `GET /api/files/{id}`：公开文件支持匿名读取；所有者可读取自己任意状态文件；其他用户仅能读取 `PUBLIC + PUBLISHED`，否则 `404`。
- 文件正文 `GET /api/files/{id}/content`：权限同文件详情；内部正文返回 `text/markdown` 并带 `Cache-Control: no-store`，外部 `mdUrl` 为 `http(s)` 时 `302`。
- 公开文件分页 `GET /api/files` 支持匿名访问，只返回 `PUBLIC + PUBLISHED` 文件。
- 管理员不能通过普通文件接口读取其他用户私有/草稿正文，管理端仅使用元数据 `AdminFileVO`。
- 当前用户文件列表 `GET /api/files/list` 从 Token 取用户，返回该用户所有状态文件；`page` 默认 1、`size` 默认 10 且范围 1-20，非法值返回 `400`。
- 旧接口 `/api/files/user/{userId}` 已移除，避免客户端传 `userId` 越权查询。
- `coverUrl` 取 `images[0].url`，无图片时为 `null`。
- 文件详情返回 `images` 数组，不返回 `coverUrl`、`content`、`md_object_key`。
- 用户接口不返回 `password_hash`。
- 已实现登录、注册、当前用户、资料修改和自助改密：`POST /api/auth/login`、`POST /api/auth/register`、`GET /api/auth/me`、`PUT /api/auth/me`、`PUT /api/auth/me/password`。
- 注册默认角色 `USER`、状态 `1`，用户名 3-50 位字母/数字/下划线；注册成功直接返回 `LoginVO`。
- 注册密码必须为 10 位，恰好包含 8 位数字和 2 位英文字母，顺序不限；`confirmPassword` 必须与密码一致，后端必须复校验。
- 注册必须提交 `captchaId` + `captchaCode`，验证码通过 `GET /api/auth/captcha` 获取；4 位、120 秒有效、一次性、不区分大小写，当前存内存。
- 登录不校验密码格式，保证历史账号（如 `admin/1234`）仍可登录；注册、管理员新增/重置密码和用户自助改密统一使用 `PasswordPolicy`，要求 10 位（8 数字 + 2 字母）。
- 登录失败按“来源 IP + 用户名”累计，15 分钟内 5 次失败后锁定 15 分钟并返回 `429`；登录成功后清除失败记录。
- Flyway `V8` 将 admin 的 `must_change_password` 置为 1；未改密前仅允许 `GET /api/auth/me` 和 `PUT /api/auth/me/password`，其他接口返回 `403`。
- 管理员重置密码会设置 `must_change_password = 1`；用户自助改密会清除该标记。
- 密码仍使用 PBKDF2-HMAC-SHA256 加随机盐哈希，禁止明文或可逆加密存储。
- 头像上传 `POST /api/users/me/avatar`：字段 `file`，`image/*`，最大 2MB，保存到 `app.upload.avatar-dir`（默认 `uploads/avatars`），返回相对 URL `/uploads/avatars/...`。
- 文件写入：`POST /api/files` 新建，`PUT /api/files/{id}` 仅所有者保存；正文存 `content`，UTF-8 最大 10MB，`visibility` 默认 `PRIVATE`，`status` 默认 `DRAFT`。
- 管理员文件分页：`GET /api/admin/files`，可选 `userId/visibility/status/keyword`，返回 `PageResult<AdminFileVO>`；不包含 `mdUrl`、`md_object_key` 和正文。
- 管理员用户写操作：`POST/PUT/PATCH/DELETE /api/users...`，要求 `ADMIN`；不能禁用/降级/删除当前登录管理员；降级/禁用/删除前保证至少保留 1 个启用中的管理员；存在文件时删除返回 `409`。
- 登录密码使用 `PasswordUtil` 的 PBKDF2-HMAC-SHA256 哈希校验；`V2` 已更新 admin 初始密码。
- 除 `/api/auth/login`、`/api/auth/register`、`/api/auth/captcha` 外，`/api/**` 统一经过 `AuthInterceptor`；公开文件列表、详情和正文通过 `@OptionalAuth` 支持匿名访问。
- 管理员接口通过 `@RequireAdmin` 注解判断，不再匹配原始 URI 字符串。
- 同级文件/文件夹名称由 Service 预检查 + `uk_files_user_parent_title` 唯一索引双重保证，冲突返回 `409`；创建文件/文件夹使用事务。
- JWT payload 包含 `sub`（用户 ID）、`username`、`role`、`ver`；`AuthInterceptor` 每次请求回查数据库，校验用户存在、`status = 1`、角色和 `token_version` 一致。
- 禁用用户、修改角色、重置密码或用户自助改密时递增 `token_version`，旧 Token 立即失效；当前用户头像上传不要求 ADMIN。
- `UserContext` 使用 `ThreadLocal` 保存用户 ID 和角色，`afterCompletion` 必须清理。
- 已配置 CORS，开发默认允许 `http://localhost:5173`、`http://127.0.0.1:5173`；生产通过 `CORS_ALLOWED_ORIGINS` 配置，不使用 Cookie 凭证。
- 本地头像静态资源通过 `/uploads/avatars/**` 暴露；开发环境 Vite 需代理 `/uploads`。

---

## 9. 本地开发与验证

### 9.1 编译

本机 JDK 23 位于 `D:\Program Files\JDK`，Maven 位于：

```text
D:\Program Files (x86)\filesToSpace\apache-maven-3.9.4-bin\apache-maven-3.9.4\bin\mvn.cmd
```

默认 Maven 镜像曾指向不可用的 `localhost:8081` Nexus；本机临时 settings：

```text
C:\Users\COLORFUL\AppData\Local\Temp\opencode\maven-settings.xml
```

命令：

```powershell
$env:JAVA_HOME = "D:\Program Files\JDK"
& "D:\Program Files (x86)\filesToSpace\apache-maven-3.9.4-bin\apache-maven-3.9.4\bin\mvn.cmd" `
  -B -s "$env:TEMP\opencode\maven-settings.xml" -DskipTests clean compile
```

打包：

```powershell
& "D:\Program Files (x86)\filesToSpace\apache-maven-3.9.4-bin\apache-maven-3.9.4\bin\mvn.cmd" `
  -B -s "$env:TEMP\opencode\maven-settings.xml" -DskipTests package
```

看到 `BUILD SUCCESS` 才算通过。

### 9.2 运行

```powershell
$env:DB_PASSWORD = "<本地数据库密码>"
$env:JWT_SECRET = "<本地随机长字符串>"
$env:SPRING_PROFILES_ACTIVE = "local"
$env:SERVER_PORT = "8081"
java -jar target\mdreader-0.0.1-SNAPSHOT.jar
```

验证时：

- 如果 8080 已被用户或 IDE 占用，使用 8081，不要杀掉用户进程。
- 启动后检查 `Tomcat started on port ...` 和 `Started MdReaderApplication`。
- 用 `curl.exe` 验证接口。
- 验证用的临时文件数据必须清理。
- 验证完成后停止自己启动的实例。
- 不把测试数据、临时日志留在仓库。

### 9.3 自动化测试

```powershell
$env:JAVA_HOME = "D:\Program Files\JDK"
& "D:\Program Files (x86)\filesToSpace\apache-maven-3.9.4-bin\apache-maven-3.9.4\bin\mvn.cmd" `
  -B -s "$env:TEMP\opencode\maven-settings.xml" test
```

- 测试使用独立数据库 `mdreader_test`，通过 `createDatabaseIfNotExist=true` 自动创建。
- 测试 profile：`backend/src/test/resources/application-test.yml`。
- 测试类位于 `backend/src/test/java/com/mdreader/integration`，继承 `BaseIntegrationTest`。
- 测试方法使用 `@Transactional` 回滚，不污染开发库 `mdreader`。
- 当前覆盖：登录/限流、Token 失效、强制改密、管理员权限、公开/私有文件、管理端正文隔离、重名、异常映射。

### 9.4 数据库

本机 MySQL 8.0.34，数据库密码通过本地环境变量配置，不要写入仓库；数据库名 `mdreader`。

MySQL Windows 服务可能因权限无法启动，当前可能由手动 `mysqld` 进程提供 3306 服务；不要随意结束未知的 `mysqld` 进程。

---

## 10. 文档维护

- 后端接口/字段/SQL 变更时，同步更新 `docs/api/backend-api-v1.0.md`。
- 文档包含：接口参数、响应示例、错误码、字段定义、完整 DDL、索引约束、种子数据、前端 TypeScript 类型。
- 新增接口时，不能只改代码不更新接口文档。
- 若接口契约变化，先确认前端字段定义是否需要同步。
- 目标契约文档：`docs/api/接口契约-当前用户文件列表-v1.0.md`。登录与 Token 方案、`GET /api/files/list`、`page/size` 分页、`message` 响应字段和 `yyyy-MM-dd HH:mm:ss` 时间格式均已实现。

---

## 11. 交付检查清单

修改后端后至少完成：

- [ ] 代码符合 Controller / Service / Mapper / XML 分层。
- [ ] 新 Bean 使用 `@RequiredArgsConstructor` 构造器注入。
- [ ] DO / DTO / VO 使用 `@Getter` / `@Setter`，字段有中文用途注释。
- [ ] Service / Mapper 方法有用途、参数、返回值、策略注释。
- [ ] 没有重新引入大段 `resultMap`。
- [ ] 分页使用 `PageUtil` 和 `PageResult`。
- [ ] 业务异常使用 `BusinessException`，不泄露内部信息。
- [ ] 数据库变更通过新增 Flyway 迁移完成。
- [ ] `img_urls` 仍为对象数组结构。
- [ ] 接口文档已同步更新。
- [ ] `mvn clean compile` / `mvn package` 通过。
- [ ] 涉及接口时完成运行时 curl 验证，并清理测试数据。

---

## 12. 禁止事项

- 不擅自添加依赖、升级框架版本、修改数据库迁移历史。
- 不把 `password_hash`、`md_object_key`、JWT 密钥、数据库密码返回或打印到日志。
- 不把 `img_urls` 改成字符串 URL 数组。
- 不恢复已删除的审核字段。
- 不在 Controller 中写业务逻辑或 SQL。
- 不使用字段注入。
- 不在 XML 中重新堆叠逐字段 `resultMap`。
- 不为了让接口“看起来完整”而编造未实现的功能。
- 不在未确认需求前改变公开/私有文件的可见性规则。
- 不杀掉用户正在使用的 8080 进程或未知的 MySQL 进程。
- 不执行 `git commit` / `git push`，除非用户明确要求。
