# 后端接口与数据库字段文档 v1.7

> 适用后端：`backend/`（Spring Boot 3.5.3 + MyBatis + Flyway + MySQL 8.0）
> 编写日期：2026-10-28
> 更新日期：2026-10-31（公开文章页：文章分类、摘要、作者昵称、分类计数接口）
> 数据来源：`backend/src/main/resources/db/migration/`、Controller / DTO / VO / Mapper 实际代码
> 当前已实现接口：登录、注册、当前用户、自助改密、资料修改、头像上传、用户详情/分页、用户写操作、文件详情、公开文件分页、公开文件分类计数、当前用户文件列表、新建/保存文件、正文读取、管理员全量文件分页。
> 当前用户文件列表契约：`docs/api/接口契约-当前用户文件列表-v1.0.md`，已实现 `GET /api/files/list`。

---

## 1. 环境与通用约定

### 1.1 服务信息

| 项目 | 值 |
|---|---|
| 本地服务地址 | `http://localhost:8080` |
| Context Path | 无（接口直接从 `/api` 开始） |
| 数据库 | MySQL 8.0.34 |
| 数据库名 | `mdreader` |
| 数据库字符集 | `utf8mb4` |
| 服务时区 | `Asia/Shanghai` |
| 默认激活 Profile | `local` |
| 认证方式 | JWT Bearer Token；除登录、注册外接口均需鉴权 |
| 跨域 | 已配置 CORS，开发默认允许 `http://localhost:5173`、`http://127.0.0.1:5173` |

### 1.2 通用响应结构

所有接口均返回统一 JSON：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `number` | 业务状态码，当前与 HTTP 状态码保持一致 |
| `message` | `string` | 提示信息 |
| `data` | `object \| array \| null` | 业务数据；失败时为 `null` |

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

失败：

```json
{
  "code": 404,
  "message": "文件不存在或未公开",
  "data": null
}
```

### 1.3 HTTP 状态码与错误码

| HTTP 状态 | `code` | 场景 |
|---|---|---|
| `200` | `200` | 请求成功 |
| `400` | `400` | 路径/查询参数类型错误，如 `id=abc` |
| `400` | `400` | 业务层参数校验失败 |
| `401` | `401` | 未登录，或 Token 缺失 / 无效 / 过期 |
| `403` | `403` | 角色不足，如普通用户访问 `/api/admin/**` 或 `/api/users/**` |
| `404` | `404` | 用户不存在 |
| `404` | `404` | 文件不存在，或当前用户无权读取 |
| `409` | `409` | 用户名/邮箱重复、删除存在文件的用户 |
| `404` | `404` | 接口不存在 |
| `500` | `500` | 服务器内部错误 |
| `500` | `500` | `files.img_urls` 不是合法 JSON 数组 |

参数类型错误的返回示例：

```json
{
  "code": 400,
  "message": "参数格式错误：id",
  "data": null
}
```

### 1.4 分页约定

分页参数通过 Query String 传递：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `pageNum` | `number` | 否 | `1` | 页码，从 1 开始；`null` 或 `< 1` 时按 `1` 处理 |
| `pageSize` | `number` | 否 | `10` | 每页条数；`null` 或 `< 1` 时按 `10` 处理；最大 `100` |

分页返回结构：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 0
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `records` | `T[]` | 当前页数据，无数据时为 `[]`，不会为 `null` |
| `total` | `number` | 总记录数 |
| `pageNum` | `number` | 实际生效的页码 |
| `pageSize` | `number` | 实际生效的每页条数 |
| `pages` | `number` | 总页数，`ceil(total / pageSize)`；无数据时为 `0` |

### 1.5 时间与 ID

- 时间字段类型为 `LocalDateTime`，JSON 格式统一为 `yyyy-MM-dd HH:mm:ss`，**不带时区**：
  `2026-10-28 15:54:02`。
- 数据库字段为 `DATETIME(3)`，服务端按 `Asia/Shanghai` 处理；Jackson 全局序列化/反序列化格式由 `JacksonConfig` 控制。
- 数据库主键为 `BIGINT UNSIGNED`，接口中序列化为 JSON `number`。
- 注意：JavaScript `number` 的安全整数上限为 `9007199254740991`，当前数据量下无风险；若未来 ID 超出该范围，需要改为字符串传输。

### 1.6 认证与鉴权

- 登录/注册接口：`POST /api/auth/login`、`POST /api/auth/register`、`GET /api/auth/captcha`，无需 Token。
- 除 `/api/auth/login`、`/api/auth/register`、`GET /api/auth/captcha` 外，所有 `/api/**` 接口必须携带：

```http
Authorization: Bearer <token>
```

- Token 由 `JwtUtil` 使用 HS256 签发，payload 包含 `sub`（用户 ID）、`username`、`role`、`ver`（Token 版本号）。
- `AuthInterceptor` 校验签名和过期时间，再回查数据库校验用户存在、`status = 1`、角色与 Token 一致、`token_version` 与 `ver` 一致，最后写入 `UserContext`；请求结束后清理 ThreadLocal。
- 用户被禁用、角色被修改、密码被重置或用户自助改密时，`token_version` 会递增，旧 Token 立即失效并返回 `401`。
- 管理员权限通过控制器/方法上的 `@RequireAdmin` 判断，不依赖请求 URI 字符串；普通用户返回 `403`；仅 `POST /api/users/me/avatar` 对已登录普通用户开放。
- 公开文件列表、详情和正文通过 `@OptionalAuth` 支持匿名访问；携带 Token 时仍会校验并按所有者权限读取私有/草稿文件。
- 用户 `must_change_password = 1` 时仅允许访问 `GET /api/auth/me` 和 `PUT /api/auth/me/password`，其他业务接口返回 `403 请先修改初始密码`。
- 有效期：本地默认 `180` 分钟（3 小时），生产默认 `10080` 分钟（7 天），可通过 `JWT_EXPIRATION_MINUTES` 覆盖。
- `jwt.secret` 至少 32 字节；生产必须通过 `JWT_SECRET` 注入。
- 数据库密码使用 PBKDF2-HMAC-SHA256 哈希校验，格式为 `pbkdf2_sha256$迭代次数$盐$哈希`；新密码使用 600000 次迭代，旧哈希按其记录次数校验；Flyway `V2` 已将 `admin` 的初始密码 `1234` 更新为哈希。
- 前后端分离开发已启用 CORS：开发默认允许 Vite 的 `5173` 端口，生产通过 `CORS_ALLOWED_ORIGINS` 配置前端域名；鉴权使用 `Authorization` 请求头，不依赖 Cookie。

---

## 2. 枚举定义

### 2.1 用户角色 `user.role`

| 值 | 说明 |
|---|---|
| `ADMIN` | 管理员 |
| `USER` | 普通用户 |

数据库有 `CHECK (role IN ('ADMIN', 'USER'))` 约束。

### 2.2 用户状态 `user.status`

| 值 | 类型 | 说明 |
|---|---|---|
| `1` | `number` | 启用 |
| `0` | `number` | 禁用 |

### 2.3 文件可见性 `files.visibility`

| 值 | 说明 |
|---|---|
| `PUBLIC` | 公开 |
| `PRIVATE` | 私有 |

数据库有 `CHECK (visibility IN ('PUBLIC', 'PRIVATE'))` 约束。

### 2.4 文件状态 `files.status`

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿 |
| `PENDING` | 待审核 |
| `PUBLISHED` | 已发布 |
| `REJECTED` | 已拒绝 |

数据库有 `CHECK (status IN ('DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED'))` 约束。

### 2.5 存储提供方 `files.storage_provider`

| 值 | 说明 |
|---|---|
| `ALIYUN_OSS` | 阿里云 OSS |
| `TENCENT_COS` | 腾讯云 COS |
| `S3` | S3 兼容存储 |
| `LOCAL` | 本地存储 |

数据库仅限制为 `VARCHAR(32)`，**没有** `CHECK` 约束；以上为约定取值。

---

## 3. 接口清单

| 方法 | 路径 | 说明 | 认证 |
|---|---|---|---|
| `POST` | `/api/auth/login` | 用户登录并签发 JWT | 无 |
| `GET` | `/api/auth/captcha` | 获取注册用图形验证码 | 无 |
| `POST` | `/api/auth/register` | 注册普通用户并签发 JWT | 无 |
| `GET` | `/api/auth/me` | 获取当前登录用户信息 | 必须 Token |
| `PUT` | `/api/auth/me` | 修改当前登录用户资料 | 必须 Token |
| `PUT` | `/api/auth/me/password` | 修改当前登录用户密码 | 必须 Token |
| `POST` | `/api/users/me/avatar` | 上传/更换当前用户头像 | 必须 Token |
| `GET` | `/api/users/{id}` | 查询单个用户 | `ADMIN` |
| `GET` | `/api/users` | 分页查询用户 | `ADMIN` |
| `POST` | `/api/users` | 新增用户 | `ADMIN` |
| `PUT` | `/api/users/{id}` | 修改用户 | `ADMIN` |
| `PATCH` | `/api/users/{id}/status` | 启用/禁用用户 | `ADMIN` |
| `POST` | `/api/users/{id}/reset-password` | 重置用户密码 | `ADMIN` |
| `DELETE` | `/api/users/{id}` | 删除用户 | `ADMIN` |
| `GET` | `/api/files/{id}` | 查询文件详情；公开文件可匿名，所有者可读任意状态 | 可选 Token |
| `GET` | `/api/files` | 分页查询公开已发布文件 | 可选 Token |
| `GET` | `/api/files/categories` | 查询公开文章的分类及数量 | 可选 Token |
| `GET` | `/api/files/list` | 分页查询当前登录用户的全部文件 | 必须 Token |
| `GET` | `/api/files/tree` | 查询当前用户的文件树 | 必须 Token |
| `POST` | `/api/files` | 当前用户新建文件 | 必须 Token |
| `POST` | `/api/files/folders` | 当前用户新建文件夹 | 必须 Token |
| `PUT` | `/api/files/{id}` | 当前用户保存文件 | 必须 Token，仅所有者 |
| `PATCH` | `/api/files/{id}/rename` | 当前用户重命名文件或文件夹 | 必须 Token，仅所有者 |
| `PATCH` | `/api/files/{id}/move` | 当前用户移动文件或文件夹 | 必须 Token，仅所有者 |
| `DELETE` | `/api/files/{id}` | 当前用户删除文件或文件夹 | 必须 Token，仅所有者 |
| `GET` | `/api/files/{id}/content` | 读取 Markdown 正文；公开文件可匿名 | 可选 Token |
| `GET` | `/api/admin/files` | 管理员分页查询全量文件 | `ADMIN` |

---

## 4. 接口详情

### 4.1 查询用户详情

```http
GET /api/users/{id}
```

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `number` | 是 | 用户 ID |

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "email": null,
    "role": "ADMIN",
    "status": 1,
    "createdAt": "2026-10-28 15:50:38",
    "updatedAt": "2026-10-28 15:50:38"
  }
}
```

#### 失败场景

| 条件 | HTTP / code | message |
|---|---|---|
| 用户不存在 | `404` | `用户不存在` |
| `id` 不是数字 | `400` | `参数格式错误：id` |

#### 说明

- 仅 `ADMIN` 可调用，普通用户返回 `403`。
- 不返回 `password_hash`。
- `email`、`nickname`、`avatar` 允许为 `null`。

---

### 4.2 分页查询用户

```http
GET /api/users?pageNum=1&pageSize=10&keyword=admin&role=ADMIN&status=1
```

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNum` | `number` | 否 | 页码，默认 `1` |
| `pageSize` | `number` | 否 | 每页条数，默认 `10`，最大 `100` |
| `keyword` | `string` | 否 | 模糊匹配 `username` 或 `email` |
| `role` | `string` | 否 | 精确匹配 `ADMIN` / `USER` |
| `status` | `number` | 否 | 精确匹配 `1` / `0` |

#### 排序

按 `created_at DESC` 排序。

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "username": "admin",
        "email": null,
        "role": "ADMIN",
        "status": 1,
        "createdAt": "2026-10-28 15:50:38",
        "updatedAt": "2026-10-28 15:50:38"
      }
    ],
    "total": 1,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

#### 说明

- 仅 `ADMIN` 可调用，普通用户返回 `403`。
- `keyword` 为空字符串或 `null` 时不参与筛选。
- `role` / `status` 为空时不参与筛选。
- 没有匹配数据时 `records` 为 `[]`，`total` 为 `0`，`pages` 为 `0`。

---

### 4.3 查询文件详情

```http
GET /api/files/{id}
```

无需 Token 即可读取 `PUBLIC + PUBLISHED` 文件；携带 Token 时，所有者可读取自己的私有/草稿文件。

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | `number` | 是 | 文件 ID |

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "userId": 1,
    "title": "公开文章",
    "originalName": "public.md",
    "mdUrl": "https://example.com/public.md",
    "storageProvider": "ALIYUN_OSS",
    "images": [
      {
        "url": "https://example.com/a.png",
        "objectKey": "img/a.png",
        "provider": "ALIYUN_OSS",
        "alt": "示例图"
      }
    ],
    "fileSize": 1234,
    "visibility": "PUBLIC",
    "status": "PUBLISHED",
    "createdAt": "2026-10-28 15:54:02",
    "updatedAt": "2026-10-28 15:54:02"
  }
}
```

#### 失败场景

| 条件 | HTTP / code | message |
|---|---|---|
| 文件不存在 | `404` | `文件不存在或未公开` |
| 非所有者读取 `PRIVATE` 或非 `PUBLISHED` 文件 | `404` | `文件不存在或未公开` |
| `img_urls` JSON 非法 | `500` | `图片数据格式错误` |
| `id` 不是数字 | `400` | `参数格式错误：id` |

#### 说明

- 文件所有者可通过该接口读取自己任意状态的文件，包括私有和草稿。
- 其他用户仅能读取 `visibility = 'PUBLIC'` 且 `status = 'PUBLISHED'` 的文件。
- `images` 来自数据库 `files.img_urls`，无图片时返回 `[]`。
- 不返回内部的 `md_object_key` 和正文；正文通过 `/api/files/{id}/content` 读取。

---

### 4.4 分页查询公开文件

```http
GET /api/files?pageNum=1&pageSize=10&keyword=Markdown&category=技术随笔
```

该接口无需 Token，仅返回 `PUBLIC + PUBLISHED` 文件。

#### 查询参数

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNum` | `number` | 否 | 页码，默认 `1` |
| `pageSize` | `number` | 否 | 每页条数，默认 `10`，最大 `100` |
| `keyword` | `string` | 否 | 按 `title` 模糊匹配 |
| `category` | `string` | 否 | 按文章分类精确匹配；传 `UNCATEGORIZED` 表示未分类 |

#### 固定筛选条件

- `visibility = 'PUBLIC'`
- `status = 'PUBLISHED'`

#### 排序

按 `created_at DESC` 排序。

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1,
        "title": "公开文章",
        "originalName": "public.md",
        "mdUrl": "https://example.com/public.md",
        "storageProvider": "ALIYUN_OSS",
        "coverUrl": "https://example.com/a.png",
        "fileSize": 1234,
        "visibility": "PUBLIC",
        "status": "PUBLISHED",
        "createdAt": "2026-10-28 15:54:02",
        "updatedAt": "2026-10-28 15:54:02"
      }
    ],
    "total": 1,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 1
  }
}
```

#### 说明

- 列表项不返回完整 `images`，只返回 `coverUrl`。
- `coverUrl` 取 `images[0].url`；没有图片时为 `null`。
- 草稿、待审核、已拒绝、私有文件不会出现在该接口中。
- `category` 为文章分类，未设置时为 `null`。
- `summary` 为文章摘要，未填写时由服务端按正文自动生成，正文也为空时为 `null`。
- `authorNickname` 为作者昵称，作者未设置昵称时为 `null`；`authorUsername` 始终为作者登录用户名。

### 4.4.1 查询公开文章分类及数量

```http
GET /api/files/categories
```

无需 Token，用于公开文章页左侧“文章的种类”列表与计数。

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    { "category": "技术随笔", "count": 12 },
    { "category": "读书笔记", "count": 5 },
    { "category": null, "count": 3 }
  ]
}
```

#### 说明

- 只统计 `PUBLIC + PUBLISHED` 且 `node_type = 'FILE'` 的文件。
- `category` 为 `null` 的项表示未分类文章，前端展示为“未分类”，回查列表时传 `category=UNCATEGORIZED`。
- 按数量倒序排列，数量相同时按分类名升序。
- 无数据返回 `[]`。

---

### 4.5 分页查询当前用户的文件

```http
GET /api/files/list?page=1&size=10
```

#### 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <token>` |

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | `number` | 否 | `1` | 页码，从 1 开始；小于 1 返回 `400` |
| `size` | `number` | 否 | `10` | 每页条数，范围 `1-20`；超出范围返回 `400` |

#### 排序

按 `updated_at DESC` 降序；同一时间按 `id DESC` 兜底，保证分页稳定。

#### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 1,
    "records": [
      {
        "id": 2,
        "title": "草稿文章",
        "originalName": "draft.md",
        "fileSize": 567,
        "createdAt": "2026-10-28 15:54:02",
        "updatedAt": "2026-10-28 15:54:02"
      }
    ]
  }
}
```

#### 说明

- 当前用户由 Token 解析得到，请求中不传 `userId`。
- 返回当前用户的**全部**文件，包括 `PRIVATE`、`DRAFT`、`PENDING`、`REJECTED`。
- 列表只返回 `id`、`title`、`originalName`、`fileSize`、`createdAt`、`updatedAt`；正文和图片通过详情接口获取。
- 无数据时 `records` 为 `[]`，不会为 `null`。
- 参数为空时使用默认值；非整数返回 `400 参数格式错误：page/size`。
- 旧的 `GET /api/files/user/{userId}` 已移除，避免通过客户端传入用户 ID 越权查询。

---

### 4.6 用户登录

```http
POST /api/auth/login
```

#### 请求体

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | `string` | 是 | 登录用户名 |
| `password` | `string` | 是 | 登录密码 |

```json
{
  "username": "admin",
  "password": "1234"
}
```

#### 成功响应（HTTP 200）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 10800,
    "user": {
      "id": 1,
      "username": "admin",
      "email": null,
      "role": "ADMIN",
      "status": 1,
      "mustChangePassword": true,
      "createdAt": "2026-10-28 15:50:38",
      "updatedAt": "2026-10-28 15:50:38"
    }
  }
}
```

#### 失败场景

| 条件 | HTTP / code | message |
|---|---|---|
| 用户名或密码为空 | `400` | `用户名和密码不能为空` |
| 用户名或密码错误 | `401` | `用户名或密码错误` |
| 连续失败达到上限 | `429` | `登录失败次数过多，请15分钟后再试` |
| 账号被禁用 | `403` | `账号已被禁用` |

> 登录成功但 `user.mustChangePassword = true` 时，前端必须跳转到修改密码页；改密完成前只能访问当前用户和改密接口。

登录成功后，后续请求使用：

```http
Authorization: Bearer <token>
```

### 4.7 获取当前用户信息

```http
GET /api/auth/me
```

#### 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 是 | `Bearer <token>` |

#### 成功响应（HTTP 200）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "email": null,
    "role": "ADMIN",
    "status": 1,
    "createdAt": "2026-10-28 15:50:38",
    "updatedAt": "2026-10-28 15:50:38"
  }
}
```

#### 失败场景

| 条件 | HTTP / code | message |
|---|---|---|
| 未携带 Token | `401` | `未登录，请先登录` |
| Token 无效 | `401` | `Token 无效，请重新登录` |
| Token 过期 | `401` | `登录已过期，请重新登录` |

---

### 4.7.1 获取注册图形验证码

```http
GET /api/auth/captcha
```

#### 成功响应（HTTP 200）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "captchaId": "fb0cb1f18a4b4b8d9f0f1d4e5c6a7b8c",
    "image": "data:image/png;base64,iVBORw0KGgo...",
    "expiresIn": 120
  }
}
```

#### 说明

- 无需 Token。
- 验证码为 4 位字符，去除了 `0/O/1/I` 等易混淆字符，不区分大小写。
- 有效期 120 秒，一次性使用；校验失败或成功后均失效。
- 验证码保存在当前服务实例内存中，重启服务后失效；多实例部署需要改为 Redis 或数据库存储。

---

### 4.8 用户注册

```http
POST /api/auth/register
Content-Type: application/json
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | `string` | 是 | 3-50 位字母、数字或下划线，唯一 |
| `password` | `string` | 是 | 10 位，恰好包含 8 位数字和 2 位英文字母，顺序不限 |
| `confirmPassword` | `string` | 是 | 必须与 `password` 完全一致 |
| `captchaId` | `string` | 是 | `GET /api/auth/captcha` 返回的标识 |
| `captchaCode` | `string` | 是 | 用户输入的图形验证码 |
| `nickname` | `string` | 否 | 最多 50 个字符 |
| `email` | `string` | 否 | 基础邮箱格式，唯一 |

```json
{
  "username": "linmo",
  "password": "12345678Ab",
  "confirmPassword": "12345678Ab",
  "captchaId": "fb0cb1f18a4b4b8d9f0f1d4e5c6a7b8c",
  "captchaCode": "K7M2",
  "nickname": "林墨",
  "email": "linmo@example.com"
}
```

成功响应 `data` 为 `LoginVO`，注册后直接返回 Token，无需再次登录。默认角色 `USER`、状态 `1`。密码使用 PBKDF2-HMAC-SHA256 哈希后保存，不保存明文。

失败场景：

| 条件 | HTTP / code | message |
|---|---|---|
| 用户名格式错误 | `400` | `用户名必须为3-50位字母、数字或下划线` |
| 密码格式错误 | `400` | `密码必须为10位，且包含8位数字和2位英文字母` |
| 确认密码为空 | `400` | `请再次输入密码` |
| 两次密码不一致 | `400` | `两次输入的密码不一致` |
| 验证码为空 | `400` | `请输入验证码` |
| 验证码错误 | `400` | `验证码错误` |
| 验证码过期或已使用 | `400` | `验证码已过期，请刷新后重试` |
| 邮箱格式错误 | `400` | `邮箱格式不正确` |
| 用户名已存在 | `409` | `用户名已存在` |
| 邮箱已被使用 | `409` | `邮箱已被使用` |

> 现有 `admin/1234` 等历史密码不受新格式约束，登录接口不校验密码格式；注册、管理员新增/重置密码和用户自助改密统一要求 10 位（8 位数字 + 2 位英文字母）。

---

### 4.9 修改当前用户资料

```http
PUT /api/auth/me
Authorization: Bearer <token>
Content-Type: application/json
```

请求体字段均为可选；`null` 或不传表示不修改，空字符串表示清空：

```json
{
  "nickname": "林墨",
  "email": "linmo@example.com",
  "avatar": "/uploads/avatars/xxx.png"
}
```

成功响应 `data` 为更新后的 `UserVO`。邮箱重复返回 `409`；没有任何可修改字段返回 `400`。

---

### 4.9.1 修改当前用户密码

```http
PUT /api/auth/me/password
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `oldPassword` | `string` | 是 | 当前密码 |
| `newPassword` | `string` | 是 | 新密码，10 位且包含 8 位数字和 2 位英文字母 |
| `confirmPassword` | `string` | 是 | 必须与 `newPassword` 完全一致 |

```json
{
  "oldPassword": "12345678Ab",
  "newPassword": "87654321Zz",
  "confirmPassword": "87654321Zz"
}
```

成功响应 `data` 为 `null`。修改成功后 `token_version` 递增，当前 Token 立即失效，需要重新登录。

失败场景：

| 条件 | HTTP / code | message |
|---|---|---|
| 旧密码为空 | `400` | `请输入当前密码` |
| 旧密码错误 | `400` | `当前密码不正确` |
| 新密码格式错误 | `400` | `密码必须为10位，且包含8位数字和2位英文字母` |
| 确认密码为空 | `400` | `请再次输入密码` |
| 两次密码不一致 | `400` | `两次输入的密码不一致` |
| 新旧密码相同 | `400` | `新密码不能与当前密码相同` |

---

### 4.10 上传/更换头像

```http
POST /api/users/me/avatar
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | `file` | 是 | `image/*`，不超过 2MB，支持 jpg/jpeg/png/gif/webp |

成功响应 `data` 为更新后的 `UserVO`，`avatar` 形如 `/uploads/avatars/<uuid>.png`。本地开发通过 Vite `/uploads` 代理访问；生产环境需要将上传目录持久化并配置反向代理或改为对象存储。

失败场景：空文件、超过 2MB、非图片、扩展名不支持均返回 `400`。

---

### 4.11 新建文件

```http
POST /api/files
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "title": "示例文档",
  "originalName": "example.md",
  "content": "# Markdown 正文",
  "visibility": "PRIVATE",
  "status": "DRAFT",
  "parentId": null,
  "category": "技术随笔",
  "summary": "手写摘要"
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `title` | `string` | 是 | 无 | 最多 255 个字符；同一父目录下不可重名 |
| `originalName` | `string` | 否 | `title + .md` | 最多 255 个字符 |
| `content` | `string` | 否 | `""` | Markdown 正文，UTF-8 最大 10MB |
| `visibility` | `string` | 否 | `PRIVATE` | `PUBLIC` / `PRIVATE` |
| `status` | `string` | 否 | `DRAFT` | `DRAFT` / `PENDING` / `PUBLISHED` / `REJECTED` |
| `parentId` | `number \| null` | 否 | `null` | 父文件夹 ID；`null` 表示根目录 |
| `category` | `string` | 否 | `null` | 文章分类，最多 64 个字符；为空表示未分类 |
| `summary` | `string` | 否 | 自动生成 | 文章摘要，最多 500 个字符；为空时按正文自动生成 |

成功响应 `data` 为 `FileDetailVO`。正文写入 `files.content`，`mdUrl` 固定为 `/api/files/{id}/content`，`storageProvider` 为 `LOCAL`。父文件夹不存在或不属于当前用户返回 `404`；父节点不是文件夹、同级重名分别返回 `400`、`409`。

---

### 4.12 保存文件

```http
PUT /api/files/{id}
Authorization: Bearer <token>
Content-Type: application/json
```

请求体字段均可选；`null` 表示不修改：

```json
{
  "title": "示例文档",
  "content": "# 更新后的正文",
  "visibility": "PUBLIC",
  "status": "PUBLISHED",
  "category": "技术随笔",
  "summary": "更新后的摘要"
}
```

成功响应 `data` 为保存后的 `FileDetailVO`。仅文件所有者可保存，否则返回 `403 无权修改该文件`；文件不存在返回 `404`；正文超过 10MB 返回 `400`。

分类与摘要的特殊语义：

- `category` 传空字符串表示清空为未分类；传 `null` 表示不修改。
- `summary` 传空字符串表示清空，并按当前正文重新生成摘要；传 `null` 表示不修改。
- 本次请求更新了 `content` 而未传 `summary` 时，摘要按新正文自动重新生成。
- 分类超过 64 个字符、摘要超过 500 个字符返回 `400`。

---

### 4.13 读取 Markdown 正文

```http
GET /api/files/{id}/content
Authorization: Bearer <token>   # 可选
```

成功时**不返回统一 JSON**，而是直接返回 Markdown 文本：

```http
HTTP/1.1 200 OK
Content-Type: text/markdown;charset=UTF-8
```

- 所有者携带 Token 时可读取自己任意状态文件的正文。
- 匿名用户和其他用户仅能读取 `PUBLIC + PUBLISHED` 文件。
- `ADMIN` 不再拥有普通文件接口旁路，不能读取其他用户的私有/草稿正文。
- 若 `content` 为空且 `mdUrl` 是 `http(s)` 外部地址，返回 `302` 重定向到该地址。
- 正文响应包含 `Cache-Control: no-store` 和 `X-Content-Type-Options: nosniff`。
- 无权限或正文不存在返回 `404`；可选 Token 模式下未携带 Token 不返回 `401`。
- 文件夹没有正文，读取文件夹正文返回 `400`。

---

### 4.13.1 查询当前用户文件树

```http
GET /api/files/tree
Authorization: Bearer <token>
```

响应 `data` 为根节点数组，节点结构见 `FileTreeNodeVO`：

```json
[
  {
    "id": 11,
    "parentId": null,
    "name": "笔记",
    "type": "folder",
    "path": "笔记",
    "size": 0,
    "updatedAt": "2026-10-30 17:00:00",
    "children": [
      {
        "id": 12,
        "parentId": 11,
        "name": "示例文档",
        "type": "file",
        "path": "笔记/示例文档",
        "size": 1024,
        "updatedAt": "2026-10-30 17:00:00",
        "children": []
      }
    ]
  }
]
```

规则：

- 返回当前用户全部 `FILE` 与 `FOLDER` 节点，不分页。
- 文件夹排在文件之前，同类按名称忽略大小写升序，再按 `id` 升序。
- `children` 始终为数组；叶子节点为 `[]`。
- `path` 由服务端按父链动态计算，不落库。
- 无数据返回 `[]`。

---

### 4.13.2 新建文件夹

```http
POST /api/files/folders
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "笔记",
  "parentId": null
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `name` | `string` | 是 | 无 | 最多 255 个字符，不能包含 `/`、`\` |
| `parentId` | `number \| null` | 否 | `null` | 父文件夹 ID；`null` 表示根目录 |

成功响应 `data` 为新建的 `FileTreeNodeVO`（`type` 为 `folder`）。父文件夹不存在返回 `404`，目标不是文件夹返回 `400`，同级重名返回 `409`。

---

### 4.13.3 重命名文件或文件夹

```http
PATCH /api/files/{id}/rename
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "name": "新名称"
}
```

成功响应 `data` 为 `FileTreeNodeVO`。仅所有者可操作；名称规则同新建文件夹；同一父目录下重名返回 `409`。

---

### 4.13.4 移动文件或文件夹

```http
PATCH /api/files/{id}/move
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "parentId": 11
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `parentId` | `number \| null` | 否 | `null` | 目标父文件夹 ID；`null` 表示根目录 |

成功响应 `data` 为移动后的 `FileTreeNodeVO`。不能移动到自身，也不能把文件夹移动到自己的子孙目录，否则返回 `400`；目标目录同级重名返回 `409`；目标父节点不存在返回 `404`。

---

### 4.13.5 删除文件或文件夹

```http
DELETE /api/files/{id}
Authorization: Bearer <token>
```

成功响应 `data` 为 `null`。删除文件夹会递归删除全部子孙节点；仅所有者可操作，节点不存在或不属于当前用户返回 `404`。

---

### 4.14 管理员全量文件分页

```http
GET /api/admin/files?pageNum=1&pageSize=10&userId=1&visibility=PUBLIC&status=PUBLISHED&keyword=Markdown
Authorization: Bearer <admin-token>
```

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNum` | `number` | 否 | 页码，默认 1 |
| `pageSize` | `number` | 否 | 每页条数，默认 10，最大 100 |
| `userId` | `number` | 否 | 所属用户 ID |
| `visibility` | `string` | 否 | `PUBLIC` / `PRIVATE` |
| `status` | `string` | 否 | `DRAFT` / `PENDING` / `PUBLISHED` / `REJECTED` |
| `keyword` | `string` | 否 | 标题或原始文件名模糊搜索 |

响应 `data` 为 `PageResult<AdminFileVO>`，不限制可见性和状态，按 `updated_at DESC, id DESC` 排序。`AdminFileVO` 仅包含元数据和 `coverUrl`，不包含 `mdUrl`、`md_object_key` 或正文；管理员不能通过普通文件接口读取其他用户的私有正文。

---

### 4.15 管理员用户写操作

所有接口要求 `ADMIN`：

| 方法 | 路径 | 请求体 | 说明 |
|---|---|---|---|
| `POST` | `/api/users` | `username`、`password`、`nickname?`、`email?`、`role?`、`status?` | 新增用户，返回 `UserVO` |
| `PUT` | `/api/users/{id}` | `nickname?`、`email?`、`role?`、`status?` | 修改用户，返回 `UserVO` |
| `PATCH` | `/api/users/{id}/status` | `{ "status": 0 }` | 启用/禁用，返回 `UserVO` |
| `POST` | `/api/users/{id}/reset-password` | `{ "password": "87654321Zz" }` | 重置密码，返回 `data: null` |
| `DELETE` | `/api/users/{id}` | 无 | 删除用户，返回 `data: null` |

规则：

- 新增/修改时用户名、邮箱保持唯一，重复返回 `409`。
- 角色仅允许 `ADMIN` / `USER`，状态仅允许 `0` / `1`。
- 新增和重置密码统一要求 10 位（8 位数字 + 2 位英文字母）；重置后旧 Token 立即失效。
- 不能禁用、降级或删除当前登录管理员。
- 降级、禁用或删除管理员前会检查启用中的管理员数量，系统至少保留 1 个启用中的管理员。
- 用户存在文件时删除返回 `409 该用户存在文件，无法删除`。

---

## 5. 返回对象字段定义

### 5.1 `UserVO`

| 字段 | 类型 | 可空 | 来源 | 说明 |
|---|---|---|---|---|
| `id` | `number` | 否 | `user.id` | 用户 ID |
| `username` | `string` | 否 | `user.username` | 登录用户名 |
| `nickname` | `string` | 是 | `user.nickname` | 用户昵称 |
| `avatar` | `string` | 是 | `user.avatar` | 头像访问 URL |
| `email` | `string` | 是 | `user.email` | 邮箱 |
| `role` | `"ADMIN" \| "USER"` | 否 | `user.role` | 角色 |
| `status` | `number` | 否 | `user.status` | `1` 启用，`0` 禁用 |
| `createdAt` | `string` | 否 | `user.created_at` | 创建时间 |
| `updatedAt` | `string` | 否 | `user.updated_at` | 更新时间 |

不返回的数据库字段：`password_hash`。

### 5.2 `FileVO`（列表项）

| 字段 | 类型 | 可空 | 来源 | 说明 |
|---|---|---|---|---|
| `id` | `number` | 否 | `files.id` | 文件 ID |
| `userId` | `number` | 否 | `files.user_id` | 所属用户 ID |
| `title` | `string` | 否 | `files.title` | 文档标题 |
| `originalName` | `string` | 是 | `files.original_name` | 原始文件名 |
| `mdUrl` | `string` | 否 | `files.md_url` | Markdown 文件访问 URL |
| `storageProvider` | `string` | 否 | `files.storage_provider` | 存储提供方 |
| `coverUrl` | `string` | 是 | `files.img_urls[0].url` | 封面图，取第一张图片 |
| `fileSize` | `number` | 否 | `files.file_size` | 文件大小，单位字节 |
| `visibility` | `"PUBLIC" \| "PRIVATE"` | 否 | `files.visibility` | 可见性 |
| `status` | `"DRAFT" \| "PENDING" \| "PUBLISHED" \| "REJECTED"` | 否 | `files.status` | 文件状态 |
| `category` | `string` | 是 | `files.category` | 文章分类，未设置时为 `null` |
| `summary` | `string` | 是 | `files.summary` | 文章摘要，未设置时按正文自动生成 |
| `authorNickname` | `string` | 是 | `user.nickname` | 作者昵称，未设置时为 `null` |
| `authorUsername` | `string` | 是 | `user.username` | 作者登录用户名 |
| `createdAt` | `string` | 否 | `files.created_at` | 创建时间 |
| `updatedAt` | `string` | 否 | `files.updated_at` | 更新时间 |

不返回的数据库字段：`md_object_key`、`img_urls` 原始 JSON。

### 5.2.1 `FileCategoryVO`

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `category` | `string` | 是 | 文章分类；未分类为 `null` |
| `count` | `number` | 否 | 该分类下公开已发布文件数量 |

### 5.3 `FileDetailVO`

| 字段 | 类型 | 可空 | 来源 | 说明 |
|---|---|---|---|---|
| `id` | `number` | 否 | `files.id` | 文件 ID |
| `userId` | `number` | 否 | `files.user_id` | 所属用户 ID |
| `title` | `string` | 否 | `files.title` | 文档标题 |
| `originalName` | `string` | 是 | `files.original_name` | 原始文件名 |
| `mdUrl` | `string` | 否 | `files.md_url` | Markdown 文件访问 URL |
| `storageProvider` | `string` | 否 | `files.storage_provider` | 存储提供方 |
| `images` | `ImageInfoVO[]` | 否 | `files.img_urls` | 图片对象数组，无图片时为 `[]` |
| `fileSize` | `number` | 否 | `files.file_size` | 文件大小，单位字节 |
| `visibility` | `"PUBLIC" \| "PRIVATE"` | 否 | `files.visibility` | 可见性 |
| `status` | `"DRAFT" \| "PENDING" \| "PUBLISHED" \| "REJECTED"` | 否 | `files.status` | 文件状态 |
| `category` | `string` | 是 | `files.category` | 文章分类，未设置时为 `null` |
| `summary` | `string` | 是 | `files.summary` | 文章摘要，未设置时按正文自动生成 |
| `authorNickname` | `string` | 是 | `user.nickname` | 作者昵称，未设置时为 `null` |
| `authorUsername` | `string` | 是 | `user.username` | 作者登录用户名 |
| `createdAt` | `string` | 否 | `files.created_at` | 创建时间 |
| `updatedAt` | `string` | 否 | `files.updated_at` | 更新时间 |

### 5.4 `ImageInfoVO`

对应数据库 `files.img_urls` JSON 数组中的元素：

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

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `url` | `string` | 否 | 图片访问 URL |
| `objectKey` | `string` | 是 | 对象存储 key |
| `provider` | `string` | 是 | 存储提供方 |
| `alt` | `string` | 是 | 图片替代文本 |

`img_urls` 可以为 `NULL`；此时详情接口返回 `images: []`，列表接口返回 `coverUrl: null`。

### 5.5 `LoginVO`

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `token` | `string` | 否 | JWT Token |
| `tokenType` | `string` | 否 | 固定为 `Bearer` |
| `expiresIn` | `number` | 否 | Token 有效期，单位秒 |
| `user` | `UserVO` | 否 | 登录用户信息 |

---

## 6. 数据库定义

### 6.1 数据库创建

Flyway 迁移本身不创建数据库，需要先手动创建：

```sql
CREATE DATABASE IF NOT EXISTS `mdreader`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

连接配置（`application-local.yml`）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:mdreader}?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:1234}
```

对应环境变量：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`。

### 6.2 完整 DDL

来源：`backend/src/main/resources/db/migration/V1__create_user_and_files.sql`

> 该 SQL 由 Flyway 在应用首次启动时自动执行，不需要手动重复执行。`spring.flyway.baseline-on-migrate` 当前为 `false`，首次启动前数据库应为空库。执行记录保存在 `flyway_schema_history` 表。

```sql
CREATE TABLE `user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username`      VARCHAR(50)     NOT NULL                   COMMENT '登录用户名',
    `password_hash` VARCHAR(255)    NOT NULL                   COMMENT '密码（开发期明文，上线前改为哈希）',
    `email`         VARCHAR(255)    NULL                       COMMENT '邮箱，可选',
    `role`          VARCHAR(16)     NOT NULL DEFAULT 'USER'    COMMENT '角色：ADMIN / USER',
    `status`        TINYINT         NOT NULL DEFAULT 1         COMMENT '账号状态：1 启用，0 禁用',
    `created_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    CONSTRAINT `chk_user_role` CHECK (`role` IN ('ADMIN', 'USER'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

INSERT INTO `user` (`username`, `password_hash`, `role`, `status`)
VALUES ('admin', '1234', 'ADMIN', 1);

CREATE TABLE `files` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文件 ID',
    `user_id`          BIGINT UNSIGNED NOT NULL                COMMENT '所属用户 ID',
    `title`            VARCHAR(255)    NOT NULL                COMMENT '文档标题',
    `original_name`    VARCHAR(255)    NULL                    COMMENT '原始文件名',
    `md_url`           VARCHAR(2048)   NOT NULL                COMMENT 'Markdown 文件 URL',
    `md_object_key`    VARCHAR(512)    NULL                    COMMENT '对象存储 key，用于删除/迁移',
    `storage_provider` VARCHAR(32)     NOT NULL                COMMENT 'ALIYUN_OSS/TENCENT_COS/S3/LOCAL',
    `img_urls`         JSON            NULL                    COMMENT '图片对象数组',
    `file_size`        BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '文件大小（字节）',
    `visibility`       VARCHAR(16)     NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC / PRIVATE',
    `status`           VARCHAR(16)     NOT NULL DEFAULT 'DRAFT'   COMMENT 'DRAFT/PENDING/PUBLISHED/REJECTED',
    `created_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_files_user_updated` (`user_id`, `updated_at`),
    KEY `idx_files_feed` (`visibility`, `status`, `created_at`),
    CONSTRAINT `fk_files_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `chk_files_visibility` CHECK (`visibility` IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT `chk_files_status` CHECK (`status` IN ('DRAFT', 'PENDING', 'PUBLISHED', 'REJECTED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Markdown 文件表';
```

Flyway `V3__add_user_profile_and_file_content.sql` 增量：

```sql
ALTER TABLE `user`
    ADD COLUMN `nickname` VARCHAR(50) NULL COMMENT '用户昵称' AFTER `username`,
    ADD COLUMN `avatar` VARCHAR(2048) NULL COMMENT '头像访问 URL' AFTER `nickname`;

ALTER TABLE `files`
    ADD COLUMN `content` LONGTEXT NULL COMMENT 'Markdown 正文，NULL 表示使用外部 md_url' AFTER `original_name`;
```

Flyway `V4__add_file_tree.sql` 增量：

```sql
ALTER TABLE `files`
    ADD COLUMN `parent_id` BIGINT UNSIGNED NULL COMMENT '父节点 ID；NULL 表示位于根目录' AFTER `user_id`,
    ADD COLUMN `node_type` VARCHAR(16) NOT NULL DEFAULT 'FILE' COMMENT '节点类型：FILE / FOLDER' AFTER `parent_id`;

ALTER TABLE `files`
    ADD KEY `idx_files_user_parent` (`user_id`, `parent_id`),
    ADD KEY `idx_files_user_type` (`user_id`, `node_type`);
```

Flyway `V5__add_user_token_version.sql` 增量：

```sql
ALTER TABLE `user`
    ADD COLUMN `token_version` INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'Token 版本号，改密/禁用/降权时递增' AFTER `status`;
```

Flyway `V6__enforce_unique_sibling_name.sql` 增量：

```sql
ALTER TABLE `files`
    ADD COLUMN `parent_key` BIGINT UNSIGNED
        GENERATED ALWAYS AS (IFNULL(`parent_id`, 0)) STORED
        COMMENT '用于同级唯一约束的父节点键，根目录为 0' AFTER `parent_id`;

ALTER TABLE `files`
    ADD UNIQUE KEY `uk_files_user_parent_title` (`user_id`, `parent_key`, `title`);
```

Flyway `V7__add_file_query_indexes.sql` 增量：

```sql
ALTER TABLE `files`
    ADD KEY `idx_files_public_feed` (`visibility`, `status`, `node_type`, `created_at`, `id`),
    ADD KEY `idx_files_user_type_updated` (`user_id`, `node_type`, `updated_at`, `id`);
```

Flyway `V8__add_must_change_password.sql` 增量：

```sql
ALTER TABLE `user`
    ADD COLUMN `must_change_password` TINYINT NOT NULL DEFAULT 0
        COMMENT '是否必须修改密码：1 是，0 否' AFTER `token_version`;

UPDATE `user`
SET `must_change_password` = 1
WHERE `username` = 'admin';
```

Flyway `V9__add_file_category_summary.sql` 增量：

```sql
ALTER TABLE `files`
    ADD COLUMN `category` VARCHAR(64) NULL COMMENT '文章分类，为空表示未分类' AFTER `status`,
    ADD COLUMN `summary` VARCHAR(500) NULL COMMENT '文章摘要，为空时由服务端按正文自动生成' AFTER `category`;

ALTER TABLE `files`
    ADD KEY `idx_files_public_category` (`visibility`, `status`, `node_type`, `category`, `created_at`, `id`);
```

说明：

- 采用单表树模型，文件与文件夹共用 `files` 表，`node_type` 区分。
- `parent_id` 不加自引用外键，避免递归删除时的外键顺序问题；层级与环校验在 Service 层完成。
- 同级重名同时由 Service 预检查和数据库唯一索引 `uk_files_user_parent_title (user_id, parent_key, title)` 兜底；并发写入冲突返回 `409`。
- `path` 不落库，由服务端根据父链动态计算。
- `category` 与 `summary` 只对 `node_type = 'FILE'` 有意义；文件夹不写入这两个字段。
- `summary` 允许为空，为空时由服务端按 Markdown 正文剥离标记后截取前 500 字自动生成。
- 作者昵称与用户名不落库到 `files`，由接口联查 `user` 表派生。

### 6.3 `user` 表字段

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 否 | 自增 | 主键 |
| `username` | `VARCHAR(50)` | 否 | 无 | 登录用户名，唯一 |
| `nickname` | `VARCHAR(50)` | 是 | `NULL` | 用户昵称，为空时前端使用 `username` |
| `avatar` | `VARCHAR(2048)` | 是 | `NULL` | 头像访问 URL |
| `password_hash` | `VARCHAR(255)` | 否 | 无 | PBKDF2-HMAC-SHA256 密码哈希 |
| `email` | `VARCHAR(255)` | 是 | `NULL` | 邮箱，唯一；MySQL 唯一索引允许多个 `NULL` |
| `role` | `VARCHAR(16)` | 否 | `'USER'` | `ADMIN` / `USER` |
| `status` | `TINYINT` | 否 | `1` | `1` 启用，`0` 禁用 |
| `token_version` | `INT UNSIGNED` | 否 | `0` | Token 版本号，改密/禁用/降权时递增 |
| `must_change_password` | `TINYINT` | 否 | `0` | `1` 表示必须先修改密码 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 更新时间，更新时自动刷新 |

索引与约束：

- 主键：`PRIMARY KEY (id)`
- 唯一索引：`uk_user_username (username)`
- 唯一索引：`uk_user_email (email)`
- 检查约束：`chk_user_role (role IN ('ADMIN','USER'))`

### 6.4 `files` 表字段

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | 否 | 自增 | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 否 | 无 | 所属用户 ID，外键指向 `user.id` |
| `parent_id` | `BIGINT UNSIGNED` | 是 | `NULL` | 父文件夹 ID；`NULL` 表示根目录 |
| `parent_key` | `BIGINT UNSIGNED` | 否 | 生成列 | `IFNULL(parent_id, 0)`，用于同级唯一约束 |
| `node_type` | `VARCHAR(16)` | 否 | `'FILE'` | 节点类型：`FILE` / `FOLDER` |
| `title` | `VARCHAR(255)` | 否 | 无 | 文档标题 / 文件夹名称 |
| `original_name` | `VARCHAR(255)` | 是 | `NULL` | 原始文件名 |
| `content` | `LONGTEXT` | 是 | `NULL` | Markdown 正文；为空时使用外部 `md_url` |
| `md_url` | `VARCHAR(2048)` | 否 | 无 | Markdown 文件 URL；内部存储为 `/api/files/{id}/content` |
| `md_object_key` | `VARCHAR(512)` | 是 | `NULL` | 对象存储 key，仅后端内部使用 |
| `storage_provider` | `VARCHAR(32)` | 否 | 无 | 存储提供方 |
| `img_urls` | `JSON` | 是 | `NULL` | 图片对象数组 |
| `file_size` | `BIGINT UNSIGNED` | 否 | `0` | 文件大小，单位字节 |
| `visibility` | `VARCHAR(16)` | 否 | `'PRIVATE'` | `PUBLIC` / `PRIVATE` |
| `status` | `VARCHAR(16)` | 否 | `'DRAFT'` | `DRAFT` / `PENDING` / `PUBLISHED` / `REJECTED` |
| `category` | `VARCHAR(64)` | 是 | `NULL` | 文章分类，为空表示未分类 |
| `summary` | `VARCHAR(500)` | 是 | `NULL` | 文章摘要，为空时由服务端按正文自动生成 |
| `created_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 否 | `CURRENT_TIMESTAMP(3)` | 更新时间，更新时自动刷新 |

索引与约束：

- 主键：`PRIMARY KEY (id)`
- 唯一索引：`uk_files_user_parent_title (user_id, parent_key, title)`
- 普通索引：`idx_files_user_updated (user_id, updated_at)`
- 普通索引：`idx_files_feed (visibility, status, created_at)`
- 普通索引：`idx_files_user_parent (user_id, parent_id)`
- 普通索引：`idx_files_user_type (user_id, node_type)`
- 普通索引：`idx_files_public_feed (visibility, status, node_type, created_at, id)`
- 普通索引：`idx_files_public_category (visibility, status, node_type, category, created_at, id)`
- 普通索引：`idx_files_user_type_updated (user_id, node_type, updated_at, id)`
- 外键：`fk_files_user (user_id) REFERENCES user(id) ON DELETE RESTRICT ON UPDATE CASCADE`
- 检查约束：`chk_files_visibility (visibility IN ('PUBLIC','PRIVATE'))`
- 检查约束：`chk_files_status (status IN ('DRAFT','PENDING','PUBLISHED','REJECTED'))`

### 6.5 种子数据

Flyway `V1` 插入管理员账号，`V2` 将初始密码更新为 PBKDF2 哈希：

| 用户名 | 初始密码 | 角色 | 状态 | 运行时密码字段 |
|---|---|---|---|---|
| `admin` | `1234` | `ADMIN` | `1` | `pbkdf2_sha256$...` |

Flyway `V8` 将 `admin` 的 `must_change_password` 置为 `1`，首次登录必须修改初始密码；管理员重置其他用户密码时也会设置该标记。

> 安全提示：`V1` 中的明文仅作为初始种子，`V2` 已更新为 PBKDF2-HMAC-SHA256 哈希。生产环境仍应替换默认管理员密码，并避免在文档或日志中记录真实密码。

### 6.6 可选测试数据

用于前端联调，执行后会新增两条文件记录（用户 `admin`，ID 为 `1`）：

```sql
INSERT INTO `files`
(`user_id`, `title`, `original_name`, `md_url`, `md_object_key`, `storage_provider`,
 `img_urls`, `file_size`, `visibility`, `status`)
VALUES
(1, '公开文章', 'public.md', 'https://example.com/public.md', 'md/public.md', 'ALIYUN_OSS',
 JSON_ARRAY(JSON_OBJECT(
     'url', 'https://example.com/a.png',
     'objectKey', 'img/a.png',
     'provider', 'ALIYUN_OSS',
     'alt', '示例图'
 )),
 1234, 'PUBLIC', 'PUBLISHED'),
(1, '草稿文章', 'draft.md', 'https://example.com/draft.md', 'md/draft.md', 'ALIYUN_OSS',
 JSON_ARRAY(),
 567, 'PRIVATE', 'DRAFT');
```

清理测试数据：

```sql
DELETE FROM `files` WHERE `user_id` = 1 AND `title` IN ('公开文章', '草稿文章');
```

### 6.7 当前 SQL 查询语义

| 接口 | SQL 条件 | 排序 |
|---|---|---|
| 用户详情 | `user.id = ?` | 无 |
| 用户分页 | 可选 `username/email LIKE`、`role = ?`、`status = ?` | `created_at DESC` |
| 文件详情 | `files.id = ?`；非所有者额外要求 `visibility = 'PUBLIC' AND status = 'PUBLISHED'` | 无 |
| 文件正文 | `files.id = ?`；同文件详情权限 | 无 |
| 公开文件分页 | `visibility = 'PUBLIC' AND status = 'PUBLISHED' AND node_type = 'FILE'`，可选 `title LIKE`、可选 `category` 精确匹配 | `created_at DESC, id DESC` |
| 公开文章分类统计 | `visibility = 'PUBLIC' AND status = 'PUBLISHED' AND node_type = 'FILE'`，按去空白后的 `category` 分组 | `count DESC, category ASC` |
| 当前用户文件列表 | `files.user_id = 当前 Token 用户 AND node_type = 'FILE'` | `updated_at DESC, id DESC` |
| 当前用户文件树 | `files.user_id = 当前 Token 用户`，包含 `FILE` 与 `FOLDER` | 内存组装，文件夹优先、名称升序 |
| 管理员文件分页 | `node_type = 'FILE'`，可选 `user_id`、`visibility`、`status`、`title/original_name LIKE` | `updated_at DESC, id DESC` |
| 管理员用户分页 | 可选 `username/email LIKE`、`role`、`status` | `created_at DESC` |

---

## 7. 前端 TypeScript 类型定义

可直接放入 `frontend/src/types/api.ts`，字段与当前后端返回保持一致。

```ts
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export type UserRole = 'ADMIN' | 'USER'
export type UserStatus = 0 | 1

export type FileVisibility = 'PUBLIC' | 'PRIVATE'
export type FileStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED'
export type StorageProvider = 'ALIYUN_OSS' | 'TENCENT_COS' | 'S3' | 'LOCAL'

export interface UserVO {
  id: number
  username: string
  nickname: string | null
  avatar: string | null
  email: string | null
  role: UserRole
  status: UserStatus
  createdAt: string
  updatedAt: string
}

export interface ImageInfoVO {
  url: string
  objectKey?: string | null
  provider?: string | null
  alt?: string | null
}

export interface FileVO {
  id: number
  userId: number
  title: string
  originalName: string | null
  mdUrl: string
  storageProvider: StorageProvider
  coverUrl: string | null
  fileSize: number
  visibility: FileVisibility
  status: FileStatus
  category: string | null
  summary: string | null
  authorNickname: string | null
  authorUsername: string | null
  createdAt: string
  updatedAt: string
}

export interface FileCategoryVO {
  category: string | null
  count: number
}

export interface FileDetailVO extends Omit<FileVO, 'coverUrl'> {
  images: ImageInfoVO[]
}

export type FileNodeType = 'file' | 'folder'

export interface FileTreeNodeVO {
  id: number
  parentId: number | null
  name: string
  type: FileNodeType
  path: string
  size: number
  updatedAt: string
  children: FileTreeNodeVO[]
}

export interface LoginDTO {
  username: string
  password: string
}

export interface RegisterDTO {
  username: string
  password: string
  confirmPassword: string
  captchaId: string
  captchaCode: string
  nickname?: string
  email?: string
}

export interface CaptchaVO {
  captchaId: string
  image: string
  expiresIn: number
}

export interface UpdateProfileDTO {
  nickname?: string
  email?: string
  avatar?: string
}

export interface CreateFileDTO {
  title: string
  originalName?: string
  content?: string
  visibility?: FileVisibility
  status?: FileStatus
  category?: string
  summary?: string
}

export interface UpdateFileDTO {
  title?: string
  originalName?: string
  content?: string
  visibility?: FileVisibility
  status?: FileStatus
  category?: string
  summary?: string
}

export interface LoginVO {
  token: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserVO
}

export interface UserPageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  role?: UserRole
  status?: UserStatus
}

export interface FilePageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  category?: string
}

export interface AdminFilePageQuery extends FilePageQuery {
  userId?: number
  visibility?: FileVisibility
  status?: FileStatus
}

export interface FileListItem {
  id: number
  title: string
  originalName: string | null
  fileSize: number
  createdAt: string
  updatedAt: string
}

export interface CurrentUserFileListData {
  total: number
  records: FileListItem[]
}

export interface CurrentUserFileListQuery {
  page?: number
  size?: number
}

export type UserPageResult = PageResult<UserVO>
export type FilePageResult = PageResult<FileVO>
export type CurrentUserFileListResponse = ApiResponse<CurrentUserFileListData>
```

> `storageProvider` 在数据库中仅限制长度、没有枚举约束；当前按约定的 4 个值定义联合类型，若后端新增存储提供方，需要同步扩展 `StorageProvider`。

---

## 8. 前端接入注意事项

1. **Base URL**：开发期可直接请求 `http://localhost:8080`；前端也可使用 Vite proxy。后端已配置 CORS，开发默认允许 `http://localhost:5173`、`http://127.0.0.1:5173`，生产通过 `CORS_ALLOWED_ORIGINS` 配置前端域名。携带 `Authorization` 会触发预检，拦截器已放行 `OPTIONS`。
2. **列表与详情字段不同**：列表返回 `coverUrl`，详情返回 `images`；详情接口不返回 `coverUrl`。
3. **图片空值**：`img_urls` 为 `NULL` 或空数组时，列表 `coverUrl` 为 `null`，详情 `images` 为 `[]`。
4. **敏感字段**：`password_hash`、`md_object_key` 不会通过接口返回；前端不要依赖这些字段。
5. **文件详情可见性**：所有者可通过 `/api/files/{id}` 读取自己任意状态文件；其他用户仍只能读取 `PUBLIC + PUBLISHED`。
6. **当前用户文件列表**：使用 `/api/files/list`，用户由 Token 决定；旧的 `/api/files/user/{userId}` 已移除。
7. **正文读取**：`/api/files/{id}/content` 成功时返回 `text/markdown` 原始文本，不是统一 JSON；前端不要用 JSON 解析。
8. **头像 URL**：后端返回相对路径 `/uploads/avatars/...`；开发环境需 Vite 代理 `/uploads`，生产环境需配置静态资源反向代理或改用对象存储。
9. **时间处理**：后端统一返回 `yyyy-MM-dd HH:mm:ss` 格式的本地时间字符串，前端展示时不要直接用 `new Date()` 跨时区转换；如需格式化，按本地时间字符串处理。
10. **ID 精度**：当前 ID 以 JSON number 返回；若未来 BIGINT 超出 JS 安全整数范围，需要后端改为字符串序列化。
11. **错误处理**：HTTP 状态码与 `code` 一致，前端可同时判断 `response.ok` 和 `body.code`；失败时 `data` 为 `null`。
12. **认证**：先调用 `POST /api/auth/login` 获取 `token`，后续请求携带 `Authorization: Bearer <token>`；`401` 时跳转登录，`403` 表示角色不足。
13. **注册验证码**：先调用 `GET /api/auth/captcha` 获取 `captchaId` 和图片，再提交 `captchaId + captchaCode`；验证码一次性使用，失败后需要刷新。
14. **登录限流**：同一来源 IP + 用户名 15 分钟内失败 5 次会锁定 15 分钟，返回 `429`；前端应提示用户稍后重试。
15. **改密失效**：调用 `PUT /api/auth/me/password` 成功后，旧 Token 立即失效，前端必须清除本地 Token 并跳转登录页。

---

## 9. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-28 | 首次整理用户/文件查询接口、数据库 DDL、字段定义与前端 TypeScript 类型 |
| v1.1 | 2026-10-29 | 增加 JWT 登录、当前用户接口与全接口鉴权说明；补充角色隔离和 401/403 错误码 |
| v1.2 | 2026-10-30 | 实现当前用户文件列表；统一 `message` 字段和 `yyyy-MM-dd HH:mm:ss` 时间格式；增加 CORS 与 PBKDF2 密码说明 |
| v1.3 | 2026-10-30 | 增加注册、资料修改、头像上传、文件新建/保存/正文读取、管理员全量文件与用户写操作；新增 `nickname`、`avatar`、`files.content` |
| v1.4 | 2026-10-30 | 注册新增图形验证码、确认密码和 10 位密码规则；新增 `GET /api/auth/captcha` |
| v1.5 | 2026-10-31 | 新增用户自助改密；Token 增加版本号并回查数据库；统一密码策略；登录失败限流；管理员注解鉴权；同级名称唯一约束与最后管理员保护 |
| v1.6 | 2026-10-31 | 强制初始管理员首次改密；公开文件支持匿名读取；管理端文件列表改为元数据 VO，禁止读取用户私有正文 |
| v1.7 | 2026-10-31 | 公开文章页支持真实分类：`files` 新增 `category`、`summary` 字段（Flyway V9）；新增 `GET /api/files/categories` 分类计数接口；`GET /api/files` 支持 `category` 筛选；`FileVO` / `FileDetailVO` 增加 `category`、`summary`、`authorNickname`、`authorUsername` |
