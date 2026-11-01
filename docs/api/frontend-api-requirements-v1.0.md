# 前端接口需求与字段清单 v1.1

> 适用范围：`frontend/`（Vue 3 + TypeScript）
> 编写日期：2026-10-30
> 更新日期：2026-10-31（晚间，同步前端已接入状态：公开阅读链路、文件树重命名/移动/删除 UI、管理端元数据化）
> 依据：`docs/api/backend-api-v1.0.md` v1.7、`docs/api/接口契约-当前用户文件列表-v1.0.md`、`frontend/src/api/`、`frontend/src/types/` 实际代码
> 说明：本文档区分“后端已实现”“前端已接入”“待后端提供”“待产品确认”，不把未实现接口写成既有契约。

---

## 1. 通用约定

### 1.1 服务地址与代理

- 开发环境后端：`http://localhost:8080`
- 前端开发服务器：`http://localhost:5173`
- Vite 已配置 `/api` 代理到 `http://localhost:8080`
- 生产环境通过 `VITE_API_BASE_URL` 指定 API 基础地址；兼容旧名 `VITE_API_BASE`

### 1.2 响应结构

所有接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `number` | 业务状态码，当前与 HTTP 状态码一致 |
| `message` | `string` | 提示信息，前端直接展示 |
| `data` | `object \| array \| null` | 业务数据，失败时为 `null` |

### 1.3 认证

- 登录接口：`POST /api/auth/login`
- 除登录外，所有 `/api/**` 请求携带：

```http
Authorization: Bearer <token>
```

- 前端请求封装：`frontend/src/api/http.ts`
- B 端管理员 Token：`md-reader:admin-token`
- C 端普通用户 Token：`md-reader:user-token`
- 请求封装通过 `request(path, init, scope)` 的 `scope: 'admin' | 'user'` 选择 Token，默认 `admin`
- 401 时按 scope 清除对应 Token，并触发 `md-reader:auth-expired` 事件；事件 `detail.scope` 为 `admin` 或 `user`
- 登录成功后由前端校验角色：C 端只接受 `USER`，B 端只接受 `ADMIN`
- 用户被禁用、角色变化或密码被修改后，旧 Token 立即失效并返回 `401`
- 同一来源 IP + 用户名 15 分钟内失败 5 次后登录返回 `429`，前端提示稍后再试
- 登录响应 `user.mustChangePassword = true` 时必须跳转 `/change-password`；改密前只允许访问当前用户和改密接口
- 公开文件列表、详情和正文支持匿名访问；管理端文件列表为 `AdminFileVO`，不包含 `mdUrl` 和正文

### 1.4 时间、ID 与分页

- 时间格式：`yyyy-MM-dd HH:mm:ss`，不带时区
- ID：JSON `number`
- 分页接口统一返回：

```json
{
  "records": [],
  "total": 0,
  "pageNum": 1,
  "pageSize": 10,
  "pages": 0
}
```

- 常规分页参数：`pageNum`、`pageSize`
- 例外：`GET /api/files/list` 使用 `page`、`size`，`size` 范围为 1-20

### 1.5 错误约定

| HTTP | 场景 |
|---|---|
| `400` | 参数格式或业务校验失败 |
| `401` | 未登录、Token 无效或过期 |
| `403` | 角色不足 |
| `404` | 用户/文件不存在，或文件未公开 |
| `500` | 服务端异常 |

---

## 2. 接口总览

| 端 | 功能 | 方法与路径 | 后端状态 | 前端状态 |
|---|---|---|---|---|
| 共用 | 登录 | `POST /api/auth/login` | 已实现 | B/C 端均已接入，按角色分流 |
| 共用 | 当前用户 | `GET /api/auth/me` | 已实现 | B/C 端均已接入 |
| 共用 | 用户详情 | `GET /api/users/{id}` | 已实现 | 未使用 |
| B 端 | 用户分页 | `GET /api/users` | 已实现 | 已接入 |
| B 端 | 公开文件分页 | `GET /api/files` | 已实现 | 已接入 |
| C 端 | 当前用户文件列表 | `GET /api/files/list` | 已实现 | 已接入（文件树走 `/api/files/tree`） |
| C 端 | 公开文件详情 | `GET /api/files/{id}` | 已实现 | 已接入（所有者可读私有/草稿；公开页匿名可读） |
| C 端 | 文件正文 | `GET /api/files/{id}/content` | 已实现 | 登录后按映射回填 |
| C 端 | 注册验证码 | `GET /api/auth/captcha` | 已实现 | 注册页已接入 |
| C 端 | 注册 | `POST /api/auth/register` | 已实现 | 注册页已接入，自动登录 |
| 共用 | 修改个人资料 | `PUT /api/auth/me` | 已实现 | B/C 端均已接入真实接口 |
| 共用 | 上传/更换头像 | `POST /api/users/me/avatar` | 已实现 | B/C 端均已接入真实上传 |
| C 端 | 新建/保存文件 | `POST /api/files`、`PUT /api/files/{id}` | 已实现 | 已接入真实接口 |
| B 端 | 全量文件管理 | `GET /api/admin/files` | 已实现 | 已接入筛选与元数据展示（无正文入口） |
| B 端 | 用户写操作 | `POST/PUT/PATCH/DELETE /api/users...` | 已实现 | 已接入创建/编辑/启禁用/重置/删除 |

---

## 3. B 端管理后台

### 3.1 管理员登录

```http
POST /api/auth/login
Content-Type: application/json
```

请求：

```json
{
  "username": "admin",
  "password": "1234"
}
```

响应 `data`：

```json
{
  "token": "jwt-token",
  "tokenType": "Bearer",
  "expiresIn": 10800,
  "user": {
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

前端字段类型：

```ts
interface LoginVO {
  token: string
  tokenType: 'Bearer'
  expiresIn: number
  user: UserVO
}
```

### 3.2 当前管理员

```http
GET /api/auth/me
Authorization: Bearer <token>
```

响应 `data` 为 `UserVO`。

当前 `UserVO` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 用户 ID |
| `username` | `string` | 登录用户名 |
| `nickname` | `string \| null` | 昵称 |
| `avatar` | `string \| null` | 头像 URL |
| `email` | `string \| null` | 邮箱 |
| `role` | `'ADMIN' \| 'USER'` | 角色 |
| `status` | `0 \| 1` | 0 禁用，1 启用 |
| `createdAt` | `string` | 创建时间 |
| `updatedAt` | `string` | 更新时间 |

B 端头像菜单“编辑资料”当前字段来源：

| 字段 | 当前来源 |
|---|---|
| `username` | `GET /api/auth/me` |
| `email` | `GET /api/auth/me` |
| `nickname` | `GET /api/auth/me` |
| `avatar` | `GET /api/auth/me` 或头像接口 |

当前管理员弹窗已接入 `PUT /api/auth/me`，头像已接入 `POST /api/users/me/avatar`，不再使用 localStorage。

### 3.3 用户分页

```http
GET /api/users?pageNum=1&pageSize=10&keyword=admin&role=ADMIN&status=1
Authorization: Bearer <token>
```

Query：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNum` | `number` | 否 | 页码，从 1 开始 |
| `pageSize` | `number` | 否 | 每页条数，默认 10，最大 100 |
| `keyword` | `string` | 否 | 用户名或邮箱模糊搜索 |
| `role` | `'ADMIN' \| 'USER'` | 否 | 角色筛选 |
| `status` | `0 \| 1` | 否 | 状态筛选 |

响应 `data.records[]` 为 `UserVO`，字段见 3.2。

### 3.4 公开文件分页

```http
GET /api/files?pageNum=1&pageSize=10&keyword=Markdown
Authorization: Bearer <token>
```

Query：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `pageNum` | `number` | 否 | 页码 |
| `pageSize` | `number` | 否 | 每页条数，默认 10，最大 100 |
| `keyword` | `string` | 否 | 文件标题模糊搜索 |

响应 `data.records[]`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 文件 ID |
| `userId` | `number` | 所属用户 ID |
| `title` | `string` | 标题 |
| `originalName` | `string \| null` | 原始文件名 |
| `mdUrl` | `string` | Markdown 文件访问 URL |
| `storageProvider` | `'ALIYUN_OSS' \| 'TENCENT_COS' \| 'S3' \| 'LOCAL'` | 存储提供方 |
| `coverUrl` | `string \| null` | 封面 URL |
| `fileSize` | `number` | 字节数 |
| `visibility` | `'PUBLIC' \| 'PRIVATE'` | 可见性 |
| `status` | `'DRAFT' \| 'PENDING' \| 'PUBLISHED' \| 'REJECTED'` | 状态 |
| `createdAt` | `string` | 创建时间 |
| `updatedAt` | `string` | 更新时间 |

注意：

- 该接口只返回 `PUBLIC + PUBLISHED` 文件，且支持匿名访问（`@OptionalAuth`）。
- B 端的全量文件管理已改用管理员接口 `GET /api/admin/files`，不复用本接口。
- C 端公开文章列表页与阅读页使用本接口（`api/public.ts` 的 guest 通道），支持 `keyword` 标题搜索与 `category` 分类筛选。

### 3.5 B 端接口状态

| 功能 | 接口 | 状态 |
|---|---|---|
| 全量文件分页 | `GET /api/admin/files` | 已接入 |
| 按用户/可见性/状态筛选文件 | `GET /api/admin/files?userId=&visibility=&status=` | 已接入 |
| 用户详情 | `GET /api/users/{id}` | 后端已实现，前端未用 |
| 新建用户 | `POST /api/users` | 已接入 |
| 修改用户 | `PUT /api/users/{id}` | 已接入 |
| 启用/禁用用户 | `PATCH /api/users/{id}/status` | 已接入 |
| 重置密码 | `POST /api/users/{id}/reset-password` | 已接入 |
| 删除用户 | `DELETE /api/users/{id}` | 已接入 |
| 上传/更换管理员头像 | `POST /api/users/me/avatar` | 已接入 |
| 管理员读取文件正文 | `GET /api/files/{id}/content` | 已允许 ADMIN 读取 |

---

## 4. C 端阅读器

### 4.1 C 端登录与角色

当前状态：

- C 端有独立登录页 `/login`，并复用 B 端 `AdminLoginView` 的通用 `role` 能力。
- 保存时未登录会打开 `LoginDialog`，登录成功后自动继续保存。
- 登录表单统一使用 `src/components/LoginForm.vue`，C 端通过 `role="USER"` 调用 `useUserAuth`。
- C 端 Token 存储 key：`md-reader:user-token`；B 端仍为 `md-reader:admin-token`。
- 前端会校验角色：`ADMIN` 账号从 C 端登录会提示“该账号不是普通用户，请使用管理员入口登录”；`USER` 账号也不能进入 B 端管理后台。
- 路由守卫使用 `useUserAuth().ensureSession()`，已有 USER 会话时访问 `/login` 会自动跳转。

登录请求：

```json
{
  "username": "reader",
  "password": "******"
}
```

`GET /api/auth/me` 返回 `UserVO`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 用户 ID |
| `username` | `string` | 登录用户名 |
| `nickname` | `string \| null` | 用户栏展示名称 |
| `avatar` | `string \| null` | 用户头像 URL |
| `email` | `string \| null` | 邮箱 |
| `role` | `'ADMIN' \| 'USER'` | 角色 |
| `status` | `0 \| 1` | 0 禁用，1 启用 |
| `createdAt` | `string` | 创建时间 |
| `updatedAt` | `string` | 更新时间 |

前端类型已包含 `RegisterPayload`、`CaptchaVO`、`UpdateProfilePayload`，注册/验证码/资料接口已封装在 `src/api/user.ts`；C 端 `/register` 注册页已接入，成功后自动登录。

### 4.2 当前用户文件列表

```http
GET /api/files/list?page=1&size=10
Authorization: Bearer <token>
```

Query：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| `page` | `number` | 否 | `1` | 页码，从 1 开始 |
| `size` | `number` | 否 | `10` | 每页 1-20 |

响应 `data`：

```json
{
  "total": 2,
  "records": [
    {
      "id": 7,
      "title": "示例文档",
      "originalName": "example.md",
      "fileSize": 567,
      "createdAt": "2026-10-30 09:55:35",
      "updatedAt": "2026-10-30 09:55:35"
    }
  ]
}
```

`records[]` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 文件 ID |
| `title` | `string` | 标题 |
| `originalName` | `string \| null` | 原始文件名 |
| `fileSize` | `number` | 字节数 |
| `createdAt` | `string` | 创建时间 |
| `updatedAt` | `string` | 更新时间 |

当前状态：

- API 函数 `fetchCurrentUserFiles` 已接入真实接口，使用 USER Token。
- 文件树已改为后端真实树：`GET /api/files/tree`，不再使用 Mock，也不再维护 `md-reader:remote-file-map:v1`。
- `/api/files/list` 仍保留为平铺列表兼容接口；工作区树使用 `/api/files/tree`。
- 文件树节点 `id` 直接使用后端数字 ID，前端转为字符串作为本地节点 ID。

### 4.2.1 文件树与文件夹 CRUD

```http
GET /api/files/tree
Authorization: Bearer <user-token>
```

响应 `data` 为 `FileTreeNodeVO[]`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 节点 ID |
| `parentId` | `number \| null` | 父节点 ID，`null` 表示根目录 |
| `name` | `string` | 文件/文件夹名称，文件含扩展名 |
| `type` | `'file' \| 'folder'` | 节点类型 |
| `path` | `string` | 从根目录到当前节点的路径，使用 `/` |
| `size` | `number` | 文件字节数；文件夹为 0 |
| `updatedAt` | `string` | 更新时间 |
| `children` | `FileTreeNodeVO[]` | 子节点；文件为空数组 |

配套接口：

| 操作 | 接口 | 请求体 | 响应 |
|---|---|---|---|
| 新建文件 | `POST /api/files` | `CreateFileDTO` | `FileDetailVO` |
| 新建文件夹 | `POST /api/files/folders` | `{ name, parentId }` | `FileTreeNodeVO` |
| 重命名 | `PATCH /api/files/{id}/rename` | `{ name }` | `FileTreeNodeVO` |
| 移动 | `PATCH /api/files/{id}/move` | `{ parentId }` | `FileTreeNodeVO` |
| 删除 | `DELETE /api/files/{id}` | 无 | `null` |

约束：

- `parentId` 为 `null` 表示根目录；同目录重名返回 409。
- 移动文件夹时禁止移动到自身或子孙节点下。
- 删除文件夹会递归删除全部子节点；只有所有者可操作。

### 4.3 文件详情

```http
GET /api/files/{id}
Authorization: Bearer <token>
```

响应 `data`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `number` | 文件 ID |
| `userId` | `number` | 所属用户 ID |
| `title` | `string` | 标题 |
| `originalName` | `string \| null` | 原始文件名 |
| `mdUrl` | `string` | Markdown 文件访问 URL |
| `storageProvider` | `string` | 存储提供方 |
| `images` | `ImageInfoVO[]` | 图片列表 |
| `fileSize` | `number` | 字节数 |
| `visibility` | `'PUBLIC' \| 'PRIVATE'` | 可见性 |
| `status` | `string` | 状态 |
| `createdAt` | `string` | 创建时间 |
| `updatedAt` | `string` | 更新时间 |

`ImageInfoVO`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `url` | `string` | 图片 URL |
| `objectKey` | `string \| null` | 对象存储 key |
| `provider` | `string \| null` | 存储提供方 |
| `alt` | `string \| null` | 替代文本 |

当前状态与限制：

- 文件所有者可通过该接口读取自己任意 `visibility/status` 的文件。
- 非所有者只能读取 `PUBLIC + PUBLISHED` 文件；否则返回 404，避免暴露私有文件是否存在。
- 正文新增独立接口：`GET /api/files/{id}/content`，返回 `text/markdown;charset=UTF-8`；`content` 为空且 `mdUrl` 是 http(s) 时返回 302。
- 前端 `src/api/files.ts` 已封装 `fetchFileDetail`、`fetchFileContent`；工作区选中文件后按节点 ID 直接请求正文，不再使用 remote map。

### 4.4 C 端保存与新建文件

当前 `saveFile` 已接入真实后端，不再使用占位实现。

前端保存载荷：

```ts
interface SaveFilePayload {
  id: string
  name: string
  path: string
  content: string
}
```

保存策略：

1. 工作区文件均来自后端文件树，保存统一走 `PUT /api/files/{id}`，不再自动创建本地映射文件。
2. 未登录：先把当前脏内容写入 IndexedDB `drafts`，打开 `LoginDialog`，不请求保存接口。
3. 登录成功后：刷新当前用户与文件树，恢复当前文件草稿后自动调用 `saveCurrent()` 继续保存。
4. 编辑器停止输入 4 秒：将脏内容写入 IndexedDB `drafts`。
5. 已登录且文件仍为脏：每 10 分钟静默保存一次；手动保存仍即时执行。
6. 保存成功：标记文件已保存、清除对应 IndexedDB 草稿；静默保存不弹成功提示。

新建：

```http
POST /api/files
Authorization: Bearer <user-token>
Content-Type: application/json
```

```json
{
  "title": "渲染管线：从 Markdown 到 DOM.md",
  "originalName": "渲染管线：从 Markdown 到 DOM.md",
  "content": "# Markdown 正文",
  "visibility": "PRIVATE",
  "status": "DRAFT"
}
```

更新：

```http
PUT /api/files/{id}
Authorization: Bearer <user-token>
Content-Type: application/json
```

```json
{
  "title": "渲染管线：从 Markdown 到 DOM.md",
  "originalName": "渲染管线：从 Markdown 到 DOM.md",
  "content": "# 更新后的正文"
}
```

后端限制：

- `content` 最大 10MB，按 UTF-8 字节计算。
- 标题、原始文件名最大 255 字符。
- `visibility` 仅允许 `PUBLIC/PRIVATE`，`status` 仅允许 `DRAFT/PENDING/PUBLISHED/REJECTED`。
- 只有文件所有者可更新；非所有者返回 403。

### 4.5 C 端个人资料

C 端 `ProfileView` 已接入真实接口，展示 `nickname/username/email/avatar/id`，支持编辑昵称、邮箱、上传头像和修改密码；未登录时提供登录入口。

后端已支持：

```http
GET /api/auth/me
PUT /api/auth/me
PUT /api/auth/me/password
```

修改密码请求 `{ oldPassword, newPassword, confirmPassword }`；新密码统一为 10 位（8 数字 + 2 字母）。成功后旧 Token 立即失效，前端会清除登录状态并跳转登录页。

修改资料请求：

```json
{
  "nickname": "林墨",
  "email": "linmo@example.com",
  "avatar": "https://..."
}
```

头像接口：

```http
POST /api/users/me/avatar
Content-Type: multipart/form-data
```

字段：`file`，限制 `image/*`、不超过 2MB。`AuthInterceptor` 仅对该路径允许普通用户访问 `/api/users/**`。

### 4.6 C 端设置页

当前设置页只涉及背景色/自定义背景，属于前端本地配置，暂不需要后端接口。

后续如果要做多端同步，需要：

```http
GET /api/users/me/settings
PUT /api/users/me/settings
```

字段建议：`backgroundPreset`、`customBackgroundUrl`。

---

## 5. 前端类型与接口映射

### 5.1 已实现类型

`frontend/src/types/api.ts` 当前包含：

- `ApiResponse<T>`
- `PageResult<T>`
- `UserVO`
- `LoginPayload`
- `LoginVO`
- `RegisterPayload`
- `UpdateProfilePayload`
- `ImageInfoVO`
- `FileVO`
- `FileDetailVO`
- `FileTreeNodeVO`
- `CreateFilePayload`
- `UpdateFilePayload`
- `CreateFolderPayload`
- `RenameNodePayload`
- `MoveNodePayload`
- `UserPageQuery`
- `FilePageQuery`
- `FileListItem`
- `CurrentUserFileListData`
- `CurrentUserFileListQuery`

### 5.2 前端 API 文件

| 文件 | 当前函数 | 对应接口 |
|---|---|---|
| `src/api/http.ts` | `request` / `buildQuery` / `getAuthToken` | 通用请求、双 scope Token |
| `src/api/admin.ts` | `loginAdmin` | `POST /api/auth/login` |
| `src/api/admin.ts` | `fetchAdminMe` | `GET /api/auth/me` |
| `src/api/admin.ts` | `fetchAdminUsers` | `GET /api/users` |
| `src/api/admin.ts` | `fetchAdminFiles` | `GET /api/files` |
| `src/api/user.ts` | `loginUser` | `POST /api/auth/login` |
| `src/api/user.ts` | `fetchUserMe` | `GET /api/auth/me` |
| `src/api/user.ts` | `registerUser` | `POST /api/auth/register` |
| `src/api/user.ts` | `updateUserProfile` | `PUT /api/auth/me` |
| `src/api/user.ts` | `uploadUserAvatar` | `POST /api/users/me/avatar` |
| `src/api/files.ts` | `fetchCurrentUserFiles` | `GET /api/files/list` |
| `src/api/files.ts` | `fetchFileTree` | `GET /api/files/tree` |
| `src/api/files.ts` | `fetchFileDetail` | `GET /api/files/{id}` |
| `src/api/files.ts` | `fetchFileContent` | `GET /api/files/{id}/content` |
| `src/api/files.ts` | `createFile` / `updateFile` | `POST /api/files`、`PUT /api/files/{id}` |
| `src/api/files.ts` | `createFolder` / `renameNode` / `moveNode` / `deleteNode` | 文件夹与节点 CRUD |
| `src/api/files.ts` | `saveFile` | 真实保存适配器，只更新已有文件 |
| `src/storage/workspaceDb.ts` | `read/writeWorkspaceSnapshot`、`read/write/deleteDraft` | IndexedDB 工作区与草稿持久化 |

### 5.3 字段命名注意

- 统一使用 `message`，不是 `msg`。
- 统一使用 `pageNum/pageSize`；`/api/files/list` 例外使用 `page/size`。
- 列表接口文件字段是 `coverUrl`，详情接口是 `images`，详情不返回 `coverUrl`。
- `storageProvider` 当前后端是 `String`，前端是联合类型；如果后端新增枚举值，需要同步前端类型。
- `FileVO.userId` 是所属用户 ID，不是当前登录用户 ID。

---

## 6. 当前前端接入状态

| 模块 | 状态 |
|---|---|
| B 端登录 | 已接真实接口 |
| B 端当前用户 | 已接真实接口 |
| B 端用户列表 | 已接真实接口，支持新增/编辑/启禁用/重置密码/删除 |
| B 端文件列表 | 已切到 `/api/admin/files`（`AdminFileVO`），支持用户/可见性/状态筛选与元数据展示；不再提供正文预览和 `mdUrl` |
| B 端编辑资料 | 已接真实接口 |
| B 端更换头像 | 已接真实上传接口 |
| C 端登录/注册 | 均已接入；登录弹窗提供“去注册~”入口，注册包含确认密码、图形验证码，成功后自动登录；登录失败 5 次触发 429 限流提示 |
| C 端修改密码 | 已接入 `PUT /api/auth/me/password`，成功后清除 Token 并跳转登录 |
| 首次强制改密 | 已接入 `/change-password`；admin 首次登录或管理员重置密码后必须改密 |
| B 端密码策略 | 新增用户/重置密码已同步 10 位（8 数字 + 2 字母）规则 |
| B 端文件列表 | 已切换 `AdminFileVO`，不再提供正文预览和 `mdUrl` |
| 公开文件匿名读取 | C 端可匿名调用公开文件列表、详情和正文 |
| C 端当前用户 | 已接真实接口 |
| C 端文件树 | 已接真实树；支持导入文件/目录、新建文件/文件夹、导出 Markdown；重命名/移动/删除的 UI 已接入（文件树右键菜单 + `FileNodeActionDialog`） |
| C 端文件列表接口 | `/api/files/tree` 已接入；`/api/files/list` 保留兼容 |
| C 端文件详情/正文 | 已接入登录后按节点 ID 回填 |
| C 端公开阅读 | 已接入：`/` 为公开文章列表（搜索/分类筛选/分页），`/read/:id` 为独立阅读页（目录、阅读进度、复制链接、阅读排版设置），公开接口匿名可读 |
| C 端保存 | 已接真实接口；IndexedDB 草稿、4 秒本地暂存、10 分钟静默保存 |
| C 端个人资料 | 已接真实接口，支持编辑资料与头像上传 |
| C 端设置 | 本地配置 |

---

## 7. 建议优先级

### P0：C 端登录与保存闭环（已完成）

1. C 端 `/login` 与保存弹窗登录已接入。
2. USER Token 与 ADMIN Token 已拆分。
3. 保存前本地草稿暂存、登录后自动继续保存已完成。
4. `POST /api/files`、`PUT /api/files/{id}` 真实同步已接入并验证。

### P1：文件树与正文加载（已完成）

1. 文件树统一使用后端 `GET /api/files/tree`，节点 ID 即后端文件 ID。
2. 已接入 `GET /api/files/{id}/content`，选中文件后直接回填正文。
3. 不再需要 local id -> remote id 映射；导入文件直接创建后端文件。
4. 已完成工作区工具：导入文件、导入目录、新建文件/文件夹、导出 Markdown；重命名/移动/删除的 UI 已接入（文件树右键菜单 + `FileNodeActionDialog`）。
5. 已用 IndexedDB 保存工作区快照与草稿，编辑器 4 秒暂存，登录用户 10 分钟静默保存。

### P2：个人资料与 B 端管理能力

1. 已完成：C 端 `ProfileView` 接入 `GET/PUT /api/auth/me` 与头像上传。
2. 已完成：B 端编辑资料/头像接入真实接口。
3. 已完成：管理员全量文件筛选（`GET /api/admin/files`，仅元数据）、用户创建/编辑/启禁用/重置密码/删除；管理端不提供正文预览入口。
4. 文件流向当前按 `status` 推导展示，不包含编辑/暂存/删除审计记录。

### P3：公开阅读器（已完成 MVP）

1. 已完成：`GET /api/files`、`GET /api/files/{id}`、`GET /api/files/{id}/content`、`GET /api/files/categories` 支持匿名访问。
2. 已完成：公开文章列表页（`/`）与独立阅读页（`/read/:id`）。
3. 已完成：目录锚点与高亮、阅读进度与上次位置、复制链接、阅读排版设置（字号/行高/宽度/代码字号）。
4. 待补：上下篇导航、收藏/书签、阅读历史、图片灯箱、SEO 元信息；这些需要后端新增数据表或接口。

---

## 8. 待确认问题

| 编号 | 问题 | 状态/影响 |
|---|---|---|
| API-01 | C 端与 B 端是否共用登录页/Token？ | 已确认：共用 `AdminLoginView`，Token 按 scope 分离 |
| API-02 | C 端文件树是后端返回树，还是前端根据平铺列表生成？ | 已确认：后端返回树，前端使用 `GET /api/files/tree` |
| API-03 | `mdUrl` 是否可直接 GET？是否需要鉴权？ | 已提供 `/content`；优先读 `content`，为空且为 http(s) 才 302 |
| API-04 | 保存正文提交 Markdown 字符串还是上传文件？ | 已确认：JSON 提交 `content` 字符串 |
| API-05 | 私有/草稿文件详情如何按所有者授权？ | 已实现：所有者可读，非所有者仅 PUBLIC+PUBLISHED |
| API-06 | 昵称、头像由 `UserVO` 返回还是独立接口？ | 已确认：`UserVO` 返回 `nickname/avatar`，头像另有上传接口 |
| API-07 | B 端是否需要全量文件管理？ | 已确认并接入 `/api/admin/files`，支持筛选与元数据展示；按「B 端不读正文」约定已移除正文入口 |
| API-08 | 注册是否开放？ | 已开放：`/register`，密码 10 位（8 数字 + 2 字母），确认密码 + 图形验证码，成功后自动登录 |

---

## 9. 游客模式规划

### 9.1 推荐方案：游客会话 Token

```http
POST /api/auth/guest
Content-Type: application/json
```

```json
{
  "role": "GUEST"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "guest-jwt",
    "user": {
      "id": 0,
      "nickname": "游客",
      "role": "GUEST"
    }
  }
}
```

前端规划：

- `AuthScope` 增加 `guest`；Token key 使用 `md-reader:guest-token`。
- 未登录进入工作区时，先调用 `POST /api/auth/guest`，使用 guest Token 请求公开文件树与正文。
- 游客只允许读；新建、导入、保存、重命名、移动、删除时打开登录弹窗。
- 退出登录或 guest Token 失效时清除 guest Token，但保留 IndexedDB 草稿。
- 若后端不签发 guest Token，则退化为请求头方案：`X-MD-Role: GUEST`，仅允许访问公开读接口。

### 9.2 后端需保证

- `POST /api/auth/guest` 请求体必须包含 `role: "GUEST"`，否则返回 400。
- `GET /api/files/tree` 对 GUEST 只返回 `PUBLIC + PUBLISHED` 节点，且不返回其他用户私有节点。
- `GET /api/files/{id}/content` 对 GUEST 只允许 `PUBLIC + PUBLISHED`。
- 所有写接口对 GUEST 返回 403。
- 游客 Token 建议短期有效（如 2 小时），并限制频率，避免匿名写入或滥用。
