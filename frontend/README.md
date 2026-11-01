# MD Reader · Markdown 工作台 / 公开阅读器

基于 **Vue 3.5 + Vite + TypeScript + vue-router + TinyMCE + x-markdown-vue** 的 Markdown 编辑与阅读前端。

C 端提供两条主线：**公开阅读**（匿名可读的文章列表与阅读页）与 **登录工作台**（文件树、三模式编辑、云端保存）。B 端管理后台位于 `src/admin/`，与 C 端相互独立。所有数据均来自后端 Spring Boot 接口，本项目已无 Mock 数据。

## 快速开始

```bash
cd frontend
npm install
npm run dev
```

- 开发地址：`http://localhost:5173`（端口被占用时 Vite 会自动换端口）
- 类型检查：`npm run typecheck`
- 构建：`npm run build`（会先把 TinyMCE 资源复制到 `public/tinymce`）
- 预览构建产物：`npm run preview`

开发环境 Vite 将 `/api` 代理到 `http://localhost:8080`；生产环境需由网关/反向代理转发 `/api` 与 `/uploads`，并为 History 路由配置回退（示例见 `docs/frontend/reader-deploy-2026-10-31.md`）。

## 页面与路由

| 路由 | 说明 |
| --- | --- |
| `/` | 公开文章列表：侧栏搜索、分类筛选、分页、卡片展示（匿名可访问） |
| `/read/:id` | 独立阅读页：文章头部、目录、正文、阅读进度、复制链接（匿名可访问） |
| `/workspace` | 工作区：文件树 + 三模式编辑/预览（需登录） |
| `/login` | C 端登录（复用 B 端登录页，按角色分流） |
| `/register` | 注册：确认密码 + 图形验证码，成功后自动登录 |
| `/change-password` | 修改密码；`mustChangePassword` 用户首次登录会被强制跳转到此 |
| `/settings` | 用户设置：主题与背景、阅读排版偏好 |
| `/profile` | 个人信息：昵称、邮箱、头像、修改密码 |
| `/admin/login` | B 端管理员登录 |
| `/admin` | 重定向到 `/admin/users`，未登录会跳转登录页 |
| `/admin/users` | B 端用户管理：查询、角色/状态筛选、分页、增删改、重置密码 |
| `/admin/files` | B 端文件管理：仅元数据查询与分页，**无正文入口** |
| `/books/:id/chapters/:chapterId` | 兼容旧路由，重定向到 `/read/:chapterId` |
| 其他路径 | 404 页面 |

`mode` 缺失或非法时在 `/workspace` 回落为 `wysiwyg`。文件选择不改变路由，路由只决定首次打开的文件与模式。

## 公开阅读

### 文章列表（`views/ArticleListView.vue`）

- 数据源：`GET /api/files`（`api/public.ts` 的 guest 通道），后端固定返回 `PUBLIC + PUBLISHED`。
- 查询参数：`keyword`（标题模糊搜索）、`category`（分类精确匹配）、`pageNum` / `pageSize`。
- 分类来源：`GET /api/files/categories`，返回 `[{ category, count }]` 全量计数；`category` 为 `null` 表示未分类。
- URL 同步：搜索词记为 `q`，分类记为 `kind`（`all` 不出现在 URL，未分类为 `UNCATEGORIZED`），刷新后保留。
- 卡片展示：标题、封面缩略图（`coverUrl`）、摘要（`summary`）、分类标签、作者（昵称为空时用用户名兜底）、文件大小、更新时间。
- 状态覆盖：加载态、空态（含「搜索无结果」「当前种类本页暂无文章」两种文案）、接口失败重试。

### 阅读页（`views/ArticleReaderView.vue`）

- 数据源：`GET /api/files/{id}` + `GET /api/files/{id}/content`（并发请求后合并）。
- 目录：从 Markdown 提取 1–4 级标题生成去重 slug 锚点（`composables/useReading.ts`），支持点击跳转与滚动高亮当前章节。
- 阅读进度：滚动进度条 + `localStorage` 记录上次阅读位置（key：`md-reader:progress:{id}`）。
- 操作区：复制链接、回到顶部；动态设置 `document.title`。
- 正文渲染复用 `components/PreviewPane.vue` 的 `bare` 模式（同一套 `sanitize` 渲染链路），不复用工作区布局。

### 阅读排版设置（`composables/useReading.ts`）

字号、行高、内容宽度、代码字号四项偏好，带边界钳制与容错归一化，持久化到 `localStorage`（key：`md-reader:reading-preferences:v1`）。设置页提供滑杆与重置，阅读页通过 CSS 变量实时生效。

## 工作区功能

### 布局

- 左上角：Logo 占位 + 三条线按钮；按钮始终可见，多功能栏收起时按钮下移，避免遮挡页面标题。
- 左侧多功能栏：文件树、导入入口、底部用户栏。
- 桌面端（> 820px）默认展开 264px；移动端默认收起，通过三条线按钮滑出 280px。
- 右侧：文件名、路径、实时字数、保存状态标记、保存按钮、模式切换。
- 用户栏右侧省略号打开滑出菜单；点击外部或按 Esc 关闭。

### 三种模式

| 模式 | 编辑 | 实现 |
| --- | --- | --- |
| 所见即所得 | 是 | TinyMCE 8，自定义工具栏 |
| 源码 | 是 | `<textarea>` 直接编辑 Markdown 原文 |
| 预览 | 否 | 复用 `MarkdownRenderer` + `sanitize` |

TinyMCE 工具栏包含：加粗、加细、导入导图、导入代码块、列表、清除格式、保存。

- “加细”在常规字体下映射为斜体，按钮 tooltip 已注明。
- “导入导图”选择图片后以 data URL `<img>` 插入，单张限制 4 MB。
- “导入代码块”弹窗输入语言与代码，插入 `<pre><code class="language-x">`。
- 源码模式使用 textarea 是为了保持 Markdown 原文可控。

### 文件树与文件操作

- 文件树来自后端 `GET /api/files/tree`，节点 ID 即后端文件 ID，不再维护本地 ID 映射。
- **右键菜单**：文件与文件夹均支持「重命名 / 移动 / 删除」，由 `components/FileNodeActionDialog.vue` 承载；移动可选择目标文件夹，删除有二次确认。
- **搜索**：多功能栏顶部搜索框按文件名做客户端过滤，命中项的祖先节点自动展开。
- 导入：点击左下角 `+` 直接导入；拖文件到编辑区或左侧资源管理器先弹确认框。支持 `.md`、`.markdown`、`.txt`；同名文件自动追加 ` (2)`、` (3)`；文件夹拖入会递归读取并保留目录结构。
- 导出：单个文件导出为 Markdown。

### 保存与状态

- 字数由当前编辑器内容实时统计，所见即所得模式直接读取 TinyMCE HTML，源码/预览模式读取 Markdown。
- 编辑后立即显示「未保存」，保存中显示「保存中…」，成功后短暂显示「已保存」，失败显示「保存失败」。文件树中未保存文件显示圆点。
- 保存调用后端 `POST /api/files`（新建）或 `PUT /api/files/{id}`（更新），请求体为 JSON，Markdown 正文存 `content` 字段。
- 未登录时保存会被拦截并弹出登录框，登录成功后自动继续保存。
- 离线时保存失败并提示；网络恢复/断开有 Toast。
- 本地草稿用 IndexedDB 暂存（`storage/workspaceDb.ts`），刷新后可恢复。
- 存在未保存更改时刷新/关闭会触发浏览器 `beforeunload` 通用提示。

## 背景与外观

- 预设：浅色、深色、跟随系统。
- 自定义图片：JPG/PNG/WebP，最大 2 MB，读取为 data URL 后立即应用。
- 自定义背景会叠加半透明遮罩，保证正文可读。
- 设置保存在 `localStorage`，key：`md-reader:background`。
- 跟随系统时监听 `prefers-color-scheme`，系统主题变化会实时更新。

## B 端管理后台

B 端与 C 端相互独立，代码位于 `src/admin/`，挂载在 `/admin` 下，不复用 C 端业务组件。

### 布局

- `AdminShell`：顶部条 + 左侧导航 + 右侧内容区。
- 顶部左侧圆形 Logo 点击回到 `/admin/users`；右侧管理员头像可打开菜单，支持更换头像（图片 ≤ 2 MB，上传到后端 `POST /api/users/me/avatar`）。
- 左侧导航：用户管理、文件管理。
- 移动端（≤ 860px）侧边栏变为横向可滚动导航，主内容无页面级横向溢出。

### 登录与鉴权

- 登录页：`/admin/login`，调用 `POST /api/auth/login`。
- 开发环境默认账号：`admin / 1234`（来自后端种子数据）；后端已对该账号设置 `must_change_password`，首次登录会强制跳转改密页。
- 登录成功后保存 JWT 到 `localStorage`，key：`md-reader:admin-token`；后续请求自动携带 `Authorization: Bearer <token>`。
- 进入 `/admin/**` 前会调用 `GET /api/auth/me` 恢复并校验当前用户，仅 `ADMIN` 角色可进入。
- Token 失效/缺失、接口返回 `401` 时清除本地登录态并跳转登录页，保留原目标地址。
- 「退出登录」清除 Token 并回到登录页。

### 用户管理

- 调用 `GET /api/users`，支持 `keyword`（用户名/邮箱）、`role`、`status`、`pageNum`、`pageSize`。
- 列表字段：ID、用户名、邮箱、角色、状态、创建时间。
- 支持新建、编辑、启用/禁用、重置密码、删除；删除受后端「至少保留一个启用管理员」保护。
- 空结果、加载中、接口失败均有明确状态；失败可重试。

### 文件管理

- 调用 `GET /api/admin/files`（全量文件，不限可见性与状态），支持 `userId`、`visibility`、`status`、`keyword` 筛选与分页。
- 仅展示元数据：ID、标题、所属用户 ID、原始文件名、大小、可见性、状态、分类、作者、更新时间。
- **不展示 `mdUrl`，不提供正文预览、下载、编辑入口**；页面明确提示「B 端不读取、不预览、不下载用户文件正文」。后端 `AdminFileVO` 已不含正文，管理员也无法通过普通文件接口读取他人私有正文。

### 接口与代理

- 请求封装：`src/api/http.ts`（统一处理 `{ code, message, data }`、Bearer Token、HTTP 错误和网络错误），按 `user` / `admin` / `guest` 三个 scope 区分 Token 通道。
- C 端接口：`src/api/files.ts`、`src/api/user.ts`；公开阅读接口：`src/api/public.ts`；B 端接口：`src/api/admin.ts`。
- 可通过 `VITE_API_BASE_URL`（兼容旧名 `VITE_API_BASE`）指定 API 基础地址，默认同源。

## 目录结构

```text
frontend/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig*.json
├── scripts/
│   └── copy-tinymce.mjs           # 复制 TinyMCE 到 public/tinymce
├── public/
│   └── tinymce/                   # 构建时生成，已在 .gitignore 中忽略
└── src/
    ├── main.ts
    ├── App.vue                    # 背景层 + RouterView + Toast
    ├── style.css                  # 主题变量、背景层、Markdown 深色适配
    ├── router/index.ts            # C 端路由 + 登录/改密守卫
    ├── api/
    │   ├── admin.ts               # B 端用户/文件查询与写操作
    │   ├── files.ts               # C 端文件树、CRUD、保存
    │   ├── http.ts                # 统一请求封装
    │   ├── public.ts              # 公开阅读接口（guest 通道）
    │   └── user.ts                # C 端用户、验证码、改密
    ├── admin/
    │   ├── admin.css              # B 端布局与页面共享样式
    │   ├── types.ts               # B 端表格/导航类型
    │   ├── components/            # 表格、查询、分页、状态徽标、各类弹窗
    │   ├── composables/useAdminAuth.ts
    │   ├── layouts/AdminShell.vue # 顶栏、侧边栏、头像菜单
    │   ├── router/index.ts        # /admin 子路由
    │   ├── utils/format.ts        # 时间/文件大小格式化
    │   └── views/                 # 登录、用户管理、文件管理
    ├── components/
    │   ├── TopLeftEntry.vue       # Logo 占位 + 三条线按钮
    │   ├── MultiFunctionBar.vue   # 文件栏容器 + 搜索框
    │   ├── FileTree.vue           # 文件树 + 右键菜单
    │   ├── FileNodeActionDialog.vue # 重命名/移动/删除对话框
    │   ├── UserBar.vue / UserMenu.vue
    │   ├── LoginDialog.vue / LoginForm.vue
    │   ├── ChangePasswordDialog.vue
    │   ├── ModeSwitch.vue
    │   ├── WysiwygEditor.vue
    │   ├── SourceEditor.vue
    │   ├── PreviewPane.vue        # Markdown 只读预览（bare 模式供阅读页复用）
    │   ├── ImportConfirmDialog.vue
    │   └── ToastHost.vue
    ├── composables/
    │   ├── useFileExplorer.ts     # 文件树、导入、CRUD、脏状态
    │   ├── useReading.ts          # 阅读排版偏好 + 标题提取
    │   ├── useBackground.ts       # 主题/背景持久化
    │   ├── useCurrentUser.ts / useUserAuth.ts
    │   └── useToast.ts
    ├── storage/workspaceDb.ts     # IndexedDB 草稿与快照
    ├── types/
    │   ├── api.ts                 # 后端响应、分页、UserVO/FileVO
    │   ├── book.ts
    │   ├── workspace.ts
    │   └── turndown-plugin-gfm.d.ts
    ├── utils/
    │   ├── markdown.ts            # marked / turndown / 字数统计
    │   └── password.ts            # 密码规则（与后端 PasswordPolicy 一致）
    └── views/
        ├── ArticleListView.vue    # 公开文章列表
        ├── ArticleReaderView.vue  # 独立阅读页
        ├── WorkspaceView.vue
        ├── SettingsView.vue
        ├── ProfileView.vue
        ├── RegisterView.vue
        ├── ChangePasswordView.vue
        └── NotFoundView.vue
```

## Markdown 渲染与 XSS

预览与阅读页共用：

```vue
<MarkdownRenderer
  :markdown="content"
  allow-html
  :sanitize="true"
  :enable-gfm="true"
  :enable-shiki="true"
  :custom-attrs="customAttrs"
/>
```

- `allow-html`：允许 Markdown 中的原始 HTML；
- `sanitize`：由 `rehype-sanitize` 清洗 HTML，剥离 `<script>`、`onerror`、`javascript:` 等载荷；
- `customAttrs`：外链加 `target="_blank"` + `rel="noopener noreferrer"`，图片加 `loading="lazy"` + `decoding="async"`；标题 `id` 由调用方在渲染后按顺序回填，避免 `{#id}` 原样显示。

TinyMCE 内容会在切到源码/预览/保存前通过 `turndown` 转回 Markdown；预览仍走上面的 sanitize 流程。

## 已知限制 / 待确认

- **保存冲突**：保存为整篇覆盖，未携带版本号或 `updatedAt`，多标签页/多设备同时编辑会以后写入者为准。需后端提供条件更新并返回 409。
- **图片体积**：粘贴图片与「导入导图」仍以 base64 写入 Markdown，单张限制 4 MB，多图叠加可能超过后端 10 MB 正文上限，且失败时无针对性提示。需后端提供通用图片上传接口。
- **双渲染链路**：所见即所得走 `marked`，预览走 `x-markdown-vue`，再经 TinyMCE → turndown 回转，表格/任务列表/代码块格式可能有出入，暂无往返测试。
- **密码规则**：仍为「恰好 10 位、8 数字 + 2 字母」，与后端 `PasswordPolicy` 一致但可用性偏紧，待统一放宽。
- **Token**：存在 `localStorage`，无 refresh token；已监听 `storage` 事件做跨标签页同步，失效时清除状态并跳转登录。
- **无障碍**：文件树声明了 `role="tree"` 与 `aria-expanded` / `aria-selected`，但缺方向键与 Home/End 导航；弹窗缺焦点陷阱与关闭后焦点恢复。
- **工程化**：无单元测试、组件测试、E2E 与 ESLint，仅有 `typecheck` 与 `build`。
- **体积**：`WorkspaceView` 仍为静态引入，TinyMCE / Shiki 在进入工作区时同步加载；列表页与阅读页已懒加载。
- **SEO**：仅动态设置 `document.title`，无 description / Open Graph / canonical；纯 SPA 无预渲染。
- **错误边界**：未配置 `app.config.errorHandler` 与全局错误页。
- **图片灯箱**：阅读页与预览均未实现图片点击放大。
- **文章上下篇 / 收藏 / 阅读历史**：均未实现，缺少后端数据模型。
- **自定义背景**：受浏览器 `localStorage` 容量限制，超过约 2.7 MB 的 data URL 可能持久化失败。
- **文件夹导入**：依赖浏览器 `webkitGetAsEntry`，需支持该 API 的 Chromium/Firefox/Safari 版本。

## 技术栈

- Vue 3.5（`<script setup>`）
- Vite 8
- TypeScript 6
- vue-router 4（History 模式）
- TinyMCE 8 + `@tinymce/tinymce-vue`（GPL license key，自托管资源）
- x-markdown-vue 0.0.204 + shiki/shiki-stream
- marked + turndown + turndown-plugin-gfm
- idb 8（IndexedDB 草稿）

## 验证

```bash
npm run typecheck
npm run build
```

手动检查建议：

1. `/` 展示公开文章列表，搜索与分类切换生效，分页与空态正常；
2. `/read/:id` 直接打开与刷新均正常，目录可跳转、当前章节高亮、进度条与「复制链接」可用；
3. 阅读页字号/行高/宽度调整后刷新仍保留；
4. 桌面端工作区默认展开文件栏，三条线按钮收起/展开；
5. 三种模式切换，URL 中 `mode` 同步且不产生误报未保存；
6. TinyMCE 编辑后切源码，内容一致；保存后未保存标记消失；
7. 文件树右键重命名、移动、删除均生效，删除有二次确认；
8. 多功能栏搜索框可按文件名过滤；
9. 刷新保留本地草稿；关闭标签页不保留；
10. 设置页切换浅色/深色/跟随系统、上传/重新应用/移除自定义背景；
11. 未登录时点保存弹出登录框，登录后自动继续保存；
12. 移动端 390px：文件栏默认收起、按钮可见、无横向溢出；阅读页正文无横向溢出；
13. B 端未登录访问 `/admin/users` 会跳转 `/admin/login`；错误密码提示「用户名或密码错误」；
14. 使用 `admin / 1234` 首次登录会被强制跳转改密页，改密成功后需重新登录；
15. B 端 `/admin/files`：仅元数据，无正文链接/预览/下载入口；
16. 手动写入无效 Token 后访问 `/admin/**` 会被清除并跳转登录页。
