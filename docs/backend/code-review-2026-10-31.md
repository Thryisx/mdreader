# 后端代码审查（2026-10-31）

> 审查范围：`backend/`
> 审查方式：静态阅读 Controller、Service、Mapper、配置、Flyway 迁移与当前构建产物；未修改业务代码。
> 原始结论：**本地可运行、可演示，但不建议直接公网上线。** 分层、PBKDF2、JWT、Flyway、统一异常和文件归属校验的基础是好的，主要缺口在会话失效、暴力破解防护、管理端权限边界、数据一致性、测试与部署。
> 复核日期：2026-10-31（晚间）。本次复核逐条对照当前代码重新判定状态，已修复项给出代码位置证据，未修复项保留原结论。

## 1. 结论摘要

| 等级 | 数量 | 说明 | 复核后状态 |
|---|---:|---|---|
| P0 | 6 | 上线前必须修复，涉及安全与权限边界 | 6 项全部已修复 |
| P1 | 8 | 影响功能正确性、数据一致性或可维护性 | 6 项已修复，2 项待处理 |
| P2 | 4 | 测试、部署、运维与文档交付 | 1 项已修复，3 项待处理 |

---

## 2. P0：上线前必须修复

### 2.1 JWT 只信任自身声明，不检查数据库状态

- 位置：`backend/src/main/java/com/mdreader/interceptor/AuthInterceptor.java:67-79`
- 风险：Token 中的 `role` 被直接信任；用户被禁用、管理员被降级、密码被重置后，旧 Token 仍然有效。生产默认有效期为 7 天（`application-prod.yml:15`）。
- **状态：已修复。**
- 现状证据：
  - `AuthInterceptor` 每次请求按 `userId` 回查数据库（`userMapper.selectById`），校验 `status = 1`、Token 角色与库中角色一致、`token_version` 与库中一致，任一不符返回 `401`。
  - Flyway `V5__add_user_token_version.sql` 新增 `user.token_version`；改密、禁用、降权时递增，旧 Token 立即失效。
  - `must_change_password = 1` 的用户在完成改密前只允许访问 `GET /api/auth/me` 与 `PUT /api/auth/me/password`，其余接口返回 `403`。
  - 生产 Token 有效期仍为 7 天（`application-prod.yml`），可通过 `JWT_EXPIRATION_MINUTES` 覆盖。

### 2.2 默认管理员密码与登录暴力破解

- 位置：`backend/src/main/resources/db/migration/V2__hash_admin_password.sql:4-5`、`backend/src/main/java/com/mdreader/service/impl/AuthServiceImpl.java:52-69`
- 风险：种子管理员固定为 `admin / 1234`，没有强制改密；登录接口没有验证码、失败次数限制或锁定。
- **状态：已修复。**
- 现状证据：
  - Flyway `V8__add_must_change_password.sql` 新增 `must_change_password`，并把种子 `admin` 置为 `1`，首次登录必须改密（前端 `/change-password` 页面配套）。
  - 新增 `LoginAttemptService`：按「客户端 IP + 用户名」累计失败次数，15 分钟内连续失败 5 次锁定 15 分钟，锁定期间返回 `429`；成功登录清除记录，并带 10000 条容量上限防止内存无限增长。
  - 管理员重置其他用户密码时同样会设置该标记。

### 2.3 缺少用户自助修改密码接口

- 位置：`backend/src/main/java/com/mdreader/controller/AuthController.java:23-83`
- 风险：当前只有登录、注册、当前用户和资料修改；用户无法修改自己的密码，只能依赖管理员重置。
- **状态：已修复。**
- 现状证据：
  - 新增 `PUT /api/auth/me/password`，请求体 `ChangePasswordDTO{oldPassword, newPassword, confirmPassword}`。
  - 新密码经统一 `PasswordPolicy` 校验；改密成功后 `token_version` 递增，当前 Token 立即失效，需重新登录。

### 2.4 管理员权限判断依赖原始 URI 字符串

- 位置：`backend/src/main/java/com/mdreader/interceptor/AuthInterceptor.java:109-117`
- 风险：使用 `request.getRequestURI()` 判断 `/api/admin/**` 和 `/api/users/**`，建议避免依赖原始字符串匹配，防止路径编码或匹配差异带来绕过风险。
- **状态：已修复。**
- 现状证据：
  - 新增 `@RequireAdmin` 注解，拦截器改用 `HandlerMethod.hasMethodAnnotation` + 控制器类注解判定，不再读取原始 URI 字符串。
  - `UserController` 的写操作与用户分页、`AdminFileController` 均标注 `@RequireAdmin`。
  - `AdminSecurityTest`（4 个用例）覆盖普通用户访问管理端接口返回 `403`。

### 2.5 管理端可读私有正文，且文件 VO 返回 `mdUrl`

- 位置：`backend/src/main/java/com/mdreader/service/impl/FileServiceImpl.java:454-462`、`FileServiceImpl.java:844-860`、`backend/src/main/java/com/mdreader/controller/AdminFileController.java:30-33`
- 风险：`canRead` 对 ADMIN 直接放行所有文件；`FileVO` 包含 `mdUrl`。这与“B 端不读取、不预览、不下载用户文件正文”的文档约定冲突。
- **状态：已修复。**
- 现状证据：
  - `FileServiceImpl.canRead` 已移除 ADMIN 旁路：管理员和其他用户一样，只能读取 `PUBLIC + PUBLISHED`；私有/草稿正文仅所有者可读。
  - 管理端文件列表改用独立的 `AdminFileVO`，只含元数据与 `coverUrl`，不含 `mdUrl`、`md_object_key` 或正文；`AdminFileController` 只保留分页查询这一个接口。
  - 前端 `AdminFilesView.vue` 已删除正文对话框与 `fetchAdminFileContent` 调用。

### 2.6 “公开文件”接口仍然要求登录

- 位置：`backend/src/main/java/com/mdreader/config/WebMvcConfig.java:32-36`、`FileServiceImpl.java:80-90`、`FileServiceImpl.java:254-270`
- 风险：所有 `/api/**` 都经过 JWT 拦截器，文件详情和正文服务又强制 `requireCurrentUserId()`；`PUBLIC + PUBLISHED` 文件无法匿名阅读。
- **状态：已修复（产品语义确定为“公开阅读器 + 登录工作台”）。**
- 现状证据：
  - 新增 `@OptionalAuth` 注解，公开接口允许匿名访问：`GET /api/files`（分页）、`GET /api/files/{id}`、`GET /api/files/{id}/content`、`GET /api/files/categories`。
  - 服务层 `canRead` 允许匿名用户读取 `PUBLIC + PUBLISHED` 文件。
  - 前端新增 `api/public.ts`（guest 通道）与公开文章列表页、阅读页。

---

## 3. P1：功能与数据一致性

### 3.1 更新文件标题可绕过同级重名检查

- 位置：`FileServiceImpl.java:218-243`（更新）、`FileServiceImpl.java:356-373`（重命名）
- 风险：重命名接口会调用 `assertSiblingNameAvailable`，但普通保存接口修改 `title` 时不会，导致同目录出现重名文件。
- **状态：已修复。**
- 现状证据：`updateCurrentUserFile` 在 `title` 变化时调用 `assertSiblingNameAvailable(userId, existing.getParentId(), title, id)`，并排除当前文件自身。

### 3.2 创建文件不是事务

- 位置：`FileServiceImpl.java:153-184`
- 风险：先 `insertFile`，再 `updateMdUrl`；第二步失败会留下 `md_url` 为空的脏数据。`createFolder` 同样存在插入后再查询的问题。
- **状态：已修复。**
- 现状证据：`createCurrentUserFile`、`createFolder` 等方法已加 `@Transactional`，`insertFile` 与 `updateMdUrl` 处于同一事务。

### 3.3 同级重名只靠应用层检查

- 位置：`backend/src/main/resources/mapper/FileMapper.xml:139-155`
- 风险：当前是“先 count 再 insert”，并发请求仍可能写入重复名称；MySQL 对 `parent_id IS NULL` 的唯一索引不生效。
- **状态：已修复。**
- 现状证据：
  - Flyway `V6__enforce_unique_sibling_name.sql` 新增生成列 `parent_key = IFNULL(parent_id, 0)` 与唯一索引 `uk_files_user_parent_title (user_id, parent_key, title)`。
  - Service 层捕获 `DuplicateKeyException` 并统一转换为 `409 同级目录下已存在同名文件或文件夹`。
  - `FileCollisionTest`（3 个用例）覆盖并发重名场景。

### 3.4 全局异常处理会把 405/415 变成 500

- 位置：`backend/src/main/java/com/mdreader/common/exception/GlobalExceptionHandler.java:54-59`
- 风险：`@ExceptionHandler(Exception.class)` 会兜住 `HttpRequestMethodNotSupportedException`、`HttpMediaTypeNotSupportedException`、缺少请求参数等异常。
- **状态：已修复。**
- 现状证据：`GlobalExceptionHandler` 已为 405、415、400（`HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException`、`MissingServletRequestParameterException`）、`DataIntegrityViolationException`（409）、`MaxUploadSizeExceededException`、`NoResourceFoundException`（404）分别提供处理方法，`ErrorMappingTest`（3 个用例）覆盖。

### 3.5 缺少“最后一个管理员”保护

- 位置：`backend/src/main/java/com/mdreader/service/impl/UserServiceImpl.java:181-189`
- 风险：只禁止管理员降级/禁用自己；两个管理员可以互相降级，最终系统没有 ADMIN。
- **状态：已修复。**
- 现状证据：`UserMapper.countEnabledAdmins()` 统计启用中的管理员数量，降级、禁用、删除路径均校验结果必须大于 1。

### 3.6 密码策略不一致

- 位置：`AuthServiceImpl.java:37-38`、`UserServiceImpl.java:311-315`、`backend/src/main/java/com/mdreader/utils/PasswordUtil.java:18`
- 风险：注册要求恰好 10 位且 8 数字 + 2 字母；管理员重置允许 6-64 位；PBKDF2 迭代次数为 120,000。
- **状态：部分修复。**
- 已完成：新增统一 `PasswordPolicy`，注册、改密、管理员重置、新建用户全部走同一校验；PBKDF2 迭代次数从 120,000 提升到 600,000，并保留旧哈希参数用于兼容校验。
- 仍待处理：规则本身仍是「恰好 10 位、8 数字 + 2 字母」，注册/改密可用性与密码管理器兼容性未见改善（与前端 `frontend-reader-gap` 4.10 是同一问题）。建议后续放宽为「长度 + 复杂度」并同步前端 `utils/password.ts`。

### 3.7 审核状态可被普通用户直接设置

- 位置：`FileServiceImpl.java:163-165`、`FileServiceImpl.java:235-240`
- 风险：客户端可直接传 `visibility=PUBLIC`、`status=PUBLISHED`；如果存在审核流程，会被绕过。
- **状态：待处理。**
- 现状：`createCurrentUserFile` 与 `updateCurrentUserFile` 仍直接接收客户端传入的 `visibility` / `status`，仅做枚举合法性校验（`ALLOWED_STATUSES`），普通用户可直接置为 `PUBLIC + PUBLISHED` 从而进入公开列表。当前产品没有审核流程，因此尚不构成绕过，但也没有状态机与审核记录。需产品确认后再决定是「用户自主发布」还是「管理员审核后发布」。

### 3.8 索引与分页排序

- 位置：`backend/src/main/resources/db/migration/V1__create_user_and_files.sql:30-31`、`V4__add_file_tree.sql:5-7`、`FileMapper.xml:51`、`FileMapper.xml:128`
- 风险：查询普遍带 `node_type = 'FILE'`，现有索引未覆盖；公开/管理端分页只按时间排序，没有 `id` 兜底，翻页可能重复。
- **状态：已修复。**
- 现状证据：
  - Flyway `V7__add_file_query_indexes.sql` 新增 `idx_files_public_feed (visibility, status, node_type, created_at, id)` 与 `idx_files_user_type_updated (user_id, node_type, updated_at, id)`。
  - `FileMapper.xml` 中所有分页排序都追加了 `id DESC` 兜底（如 `ORDER BY f.created_at DESC, f.id DESC`、`ORDER BY f.updated_at DESC, f.id DESC`）。

---

## 4. P2：交付与运维

### 4.1 零自动化测试

- 现状：`backend/pom.xml` 没有测试 starter，仓库没有 `backend/src/test`。
- **状态：已修复（覆盖核心权限与文件流程，尚未引入 Testcontainers）。**
- 现状证据：
  - `pom.xml` 已加入 `spring-boot-starter-test`。
  - 新增 `backend/src/test`，包含 `BaseIntegrationTest`（基于 `MockMvc` + `JdbcTemplate`，使用独立库 `mdreader_test`，测试方法整体回滚）与 6 个测试类：`AuthSecurityTest`、`AdminSecurityTest`、`FileAccessTest`、`FileCollisionTest`、`ErrorMappingTest`、`PublicArticleMetaTest`，共 25 个用例，当前全部通过。
  - 覆盖：登录与限流、改密与 Token 失效、管理端越权、文件归属校验、并发重名、错误状态码映射、公开文章元数据。
  - 未覆盖：Testcontainers MySQL（当前依赖本机 `mdreader_test` 库）。

### 4.2 缺少部署与运维基础

- 现状：没有 `Dockerfile`、`docker-compose`、健康检查、优雅停机、`backend/README.md`、后端 `.gitignore`。
- **状态：待处理。**
- 复核：仓库中确认仍无 `Dockerfile`、`docker-compose.yml`、后端 `README.md` 与后端 `.gitignore`；`pom.xml` 也未引入 `spring-boot-starter-actuator`，`application.yml` 未配置 `server.shutdown=graceful`。
- 建议：见下方 4.3 与第 6 节。

### 4.3 生产配置容易误用本地默认值

- 位置：`backend/src/main/resources/application.yml:5`、`application-local.yml:9`
- 风险：默认 profile 为 `local`，本地 JWT 密钥有弱默认值；部署时忘设 `SPRING_PROFILES_ACTIVE` 会以不安全配置启动。
- **状态：部分修复。**
- 已完成：`application.yml` 中 `jwt.secret` 已改为 `${JWT_SECRET}`（无默认值，缺失时启动失败）；`application-local.yml` 的弱默认值只存在于 local profile；生产数据库账号密码、CORS 来源同样改为必须显式注入。
- 仍待处理：`spring.profiles.active` 默认值仍是 `local`，未做到「生产环境强制显式指定 profile」；也没有启动时校验 prod 关键环境变量的守卫（例如 `DB_PASSWORD` 或 `CORS_ALLOWED_ORIGINS` 为空时直接失败）。注意 `application-prod.yml` 的 `CORS_ALLOWED_ORIGINS` 默认空串，但空串是否导致启动失败未经验证。

### 4.4 验证码、头像与缓存响应头

- 位置：`backend/src/main/java/com/mdreader/service/impl/CaptchaServiceImpl.java:37`、`backend/src/main/java/com/mdreader/service/impl/AvatarServiceImpl.java:90-104`、`backend/src/main/java/com/mdreader/controller/FileController.java:143-156`
- 风险：
  - 验证码存单机内存，无容量上限和频率限制，多实例不一致；
  - 头像只校验 `Content-Type` 与扩展名，没有校验文件魔数；
  - 私有正文响应没有 `Cache-Control: no-store`。
- **状态：部分修复。**
- 已完成：私有正文响应已加 `Cache-Control: no-store` 与 `X-Content-Type-Options: nosniff`（`FileController` 的 `/content` 与详情接口）；`LoginAttemptService` 带 10000 条容量上限。
- 仍待处理：`CaptchaServiceImpl` 仍是无容量上限的内存 Map（只有过期清理），未迁移 Redis，多实例下不一致；`AvatarServiceImpl` 仍只校验 `Content-Type` 与扩展名，没有校验文件魔数（magic bytes）。

---

## 5. 建议实施顺序

1. ~~2.1 Token 校验数据库状态，并缩短生产有效期。~~ 已完成
2. ~~2.2 默认管理员强制改密 + 登录限流。~~ 已完成
3. ~~2.3 增加自助改密接口。~~ 已完成
4. ~~2.5 明确管理端正文访问策略，移除或隔离 ADMIN 旁路。~~ 已完成
5. ~~3.1、3.2、3.3 修复文件标题重名、事务与唯一约束。~~ 已完成
6. ~~4.1 建立最小测试集。~~ 已完成（25 个用例通过）
7. 3.6 放宽密码策略并同步前端规则。
8. 3.7 明确发布/审核语义并落地状态机。
9. 4.2 补 Dockerfile、compose、健康检查、优雅停机、`backend/README.md`、后端 `.gitignore`。
10. 4.3 prod profile 启动守卫。
11. 4.4 验证码迁移 Redis 或加容量上限；头像校验 magic bytes。

## 6. 上线前验收清单

- [x] 禁用用户、降级管理员、重置密码后，旧 Token 立即失效。
- [x] `admin` 默认密码已替换（首次登录强制改密），登录有失败限流。
- [x] 用户可以自助修改密码。
- [x] 普通用户无法访问 `/api/admin/**` 与 `/api/users/**`，注解判定不依赖 URI 字符串。
- [x] 管理端正文访问策略与文档一致（管理端只读元数据）。
- [x] 同目录重名文件在并发下也无法写入（唯一索引 + 409）。
- [x] 创建文件失败不会留下 `md_url` 为空的数据（事务）。
- [x] 错误方法/错误 Content-Type 返回 405/415，而不是 500。
- [x] 至少 1 个启用中的管理员始终存在。
- [x] `mvn test` 通过，集成测试覆盖核心权限与文件流程（25 个用例）。
- [ ] 生产 profile 缺少关键环境变量时启动失败（`JWT_SECRET` 已生效，其余变量待补守卫）。
- [ ] 具备 Dockerfile / compose / 健康检查 / 优雅停机 / 后端 README 与 `.gitignore`。
- [ ] 密码策略放宽为「长度 + 复杂度」，前端规则同步。
